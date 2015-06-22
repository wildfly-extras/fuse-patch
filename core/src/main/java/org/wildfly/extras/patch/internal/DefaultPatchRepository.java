/*
 * #%L
 * Fuse Patch :: Core
 * %%
 * Copyright (C) 2015 Private
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.extras.patch.internal;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchRepository;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchSet.Action;
import org.wildfly.extras.patch.PatchSet.Record;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

final class DefaultPatchRepository implements PatchRepository {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPatchRepository.class);
    
    private final Path rootPath;

    DefaultPatchRepository(URL repoUrl) {
        if (repoUrl == null) {
            repoUrl = getConfiguredUrl();
        }
        IllegalStateAssertion.assertNotNull(repoUrl, "Cannot obtain repository URL");
        Path path = Paths.get(repoUrl.getPath());
        PatchAssertion.assertTrue(path.toFile().isDirectory(), "Repository root does not exist: " + path);
        LOG.debug("Repository location: {}", path);
        this.rootPath = path.toAbsolutePath();
    }

    @Override
    public List<PatchId> queryAvailable(final String prefix) {
        Lock.tryLock();
        try {
            return Parser.getAvailable(rootPath, prefix, false);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        Lock.tryLock();
        try {
            List<PatchId> list = Parser.getAvailable(rootPath, prefix, true);
            return list.isEmpty() ? null : list.get(0);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchSet getPatchSet(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            return Parser.readPatchSet(rootPath, patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        return addArchive(fileUrl, null);
    }
    
    @Override
    public PatchId addArchive(URL fileUrl, PatchId oneoffId) throws IOException {
        IllegalArgumentAssertion.assertNotNull(fileUrl, "fileUrl");
        IllegalArgumentAssertion.assertTrue(fileUrl.getPath().endsWith(".zip"), "Unsupported file extension: " + fileUrl);
        Lock.tryLock();
        try {
            Path sourcePath = Paths.get(fileUrl.getPath());
            PatchId patchId = PatchId.fromFile(sourcePath.toFile());
            PatchAssertion.assertFalse(queryAvailable(null).contains(patchId), "Repository already contains " + patchId);

            // Verify one-off id
            if (oneoffId != null) {
                File metadataFile = Parser.getMetadataFile(rootPath, oneoffId);
                IllegalStateAssertion.assertTrue(metadataFile.isFile(), "Cannot obtain target patch for: " + oneoffId);
            }
            
            // Collect the paths from the latest other patch sets
            Map<Path, PatchId> pathMap = new HashMap<>();
            for (PatchId auxid : Parser.getAvailable(rootPath, null, false)) {
                if (!patchId.getName().equals(auxid.getName())) {
                    for (Record rec : getPatchSet(auxid).getRecords()) {
                        pathMap.put(rec.getPath(), auxid);
                    }
                }
            }
            
            // Build the patch set
            PatchSet patchSet;
            if (oneoffId != null) {
                PatchSet oneoffSet = getPatchSet(oneoffId);
                Map<Path, Record> records = new HashMap<>();
                for (Record rec : oneoffSet.getRecords()) {
                    records.put(rec.getPath(), rec);
                }
                PatchSet sourceSet = Parser.buildPatchSetFromZip(patchId, Action.INFO, sourcePath.toFile());
                for (Record rec : sourceSet.getRecords()) {
                    records.put(rec.getPath(), rec);
                }
                patchSet = PatchSet.create(patchId, records.values(), Collections.singleton(oneoffId));
            } else {
                patchSet = Parser.buildPatchSetFromZip(patchId, Action.INFO, sourcePath.toFile());
            }
            
            // Assert no duplicate paths
            Set<PatchId> duplicates = new HashSet<>();
            for (Record rec : patchSet.getRecords()) {
                PatchId otherId = pathMap.get(rec.getPath());
                if (otherId != null) {
                    PatchLogger.error("Path '" + rec.getPath() + "' already contained in: " + otherId);
                    duplicates.add(otherId);
                }
            }
            PatchAssertion.assertTrue(duplicates.isEmpty(), "Cannot add " + patchId + " because of duplicate paths in " + duplicates);
            
            // Add to repository
            File targetFile = getPatchFile(patchId);
            targetFile.getParentFile().mkdirs();
            Files.copy(sourcePath, targetFile.toPath());
            Parser.writePatchSet(rootPath, patchSet);
            
            // Remove the source file when it was placed in the repository 
            if (sourcePath.startsWith(rootPath)) {
                sourcePath.toFile().delete();
            }
            
            String message = "Added " + patchId;
            if (oneoffId != null) {
                message += " patching " + oneoffId;
            }
            PatchLogger.info(message);
            
            return patchId;
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public void addPostCommand(PatchId patchId, String[] cmdarr) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(cmdarr, "cmdarr");
        Lock.tryLock();
        try {
            PatchSet patchSet = getPatchSet(patchId);
            List<String> commands = new ArrayList<>(patchSet.getPostCommands());
            commands.add(commandString(cmdarr));
            patchSet = PatchSet.create(patchId, patchSet.getRecords(), commands);
            try {
                Parser.writePatchSet(rootPath, patchSet);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            PatchLogger.info("Added post install command to " + patchId);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(PatchSet seedPatch, PatchId patchId) {
        Lock.tryLock();
        try {
            // Derive the target patch id from the seed patch id
            if (patchId == null) {
                IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
                patchId = getLatestAvailable(seedPatch.getPatchId().getName());
            }

            // Get the patch zip file
            File zipfile = getPatchFile(patchId);
            PatchAssertion.assertTrue(zipfile.isFile(), "Cannot obtain patch file: " + zipfile);

            PatchSet targetSet = getPatchSet(patchId);
            PatchSet smartSet = PatchSet.smartSet(seedPatch, targetSet);
            return new SmartPatch(smartSet, zipfile);
        } finally {
            Lock.unlock();
        }
    }

    static URL getConfiguredUrl() {
        String repoSpec = System.getProperty("fusepatch.repository");
        if (repoSpec == null) {
            repoSpec = System.getenv("FUSEPATCH_REPOSITORY");
        }
        if (repoSpec != null) {
            try {
                return new URL(repoSpec);
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return null;
    }

    private String commandString(String[] cmdarr) {
        StringBuffer result = new StringBuffer();
        for (String tok : cmdarr) {
            result.append(tok + " ");
        }
        return result.toString().trim();
    }

    private File getPatchFile(PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString(), patchId + ".zip")).toFile();
    }
}
