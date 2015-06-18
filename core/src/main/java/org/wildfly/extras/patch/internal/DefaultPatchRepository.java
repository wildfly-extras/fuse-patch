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

public final class DefaultPatchRepository implements PatchRepository {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPatchRepository.class);
    
    private final Path rootPath;

    public DefaultPatchRepository(URL repoUrl) {
        if (repoUrl == null) {
            repoUrl = getConfiguredUrl();
        }
        IllegalStateAssertion.assertNotNull(repoUrl, "Cannot obtain repository URL");
        Path path = Paths.get(repoUrl.getPath());
        IllegalStateAssertion.assertTrue(path.toFile().isDirectory(), "Repository root does not exist: " + path);
        this.rootPath = path.toAbsolutePath();
    }

    @Override
    public List<PatchId> queryAvailable(final String prefix) {
        return Parser.getAvailable(rootPath, prefix, false);
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        List<PatchId> list = Parser.getAvailable(rootPath, prefix, true);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public PatchSet getPatchSet(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        try {
            return Parser.readPatchSet(rootPath, patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        IllegalArgumentAssertion.assertNotNull(fileUrl, "fileUrl");
        IllegalArgumentAssertion.assertTrue(fileUrl.getPath().endsWith(".zip"), "Unsupported file extension: " + fileUrl);
        
        Path sourcePath = Paths.get(fileUrl.getPath());
        PatchId patchId = PatchId.fromFile(sourcePath.toFile());
        
        // Collect the paths from the latest other patch sets
        Map<Path, PatchId> pathMap = new HashMap<>();
        for (PatchId auxid : Parser.getAvailable(rootPath, null, false)) {
            if (!patchId.getSymbolicName().equals(auxid.getSymbolicName())) {
                for (Record rec : getPatchSet(auxid).getRecords()) {
                    pathMap.put(rec.getPath(), auxid);
                }
            }
        }
        
        // Assert no duplicate paths
        Set<PatchId> duplicates = new HashSet<>();
        PatchSet patchSet = Parser.buildPatchSetFromZip(patchId, Action.INFO, sourcePath.toFile());
        for (Record rec : patchSet.getRecords()) {
            PatchId otherId = pathMap.get(rec.getPath());
            if (otherId != null) {
                LOG.error("Path '{}' already contained in: {}", rec.getPath(), otherId);
                duplicates.add(otherId);
            }
        }
        IllegalStateAssertion.assertTrue(duplicates.isEmpty(), "Cannot add " + patchId + " because of duplicate paths in " + duplicates);
        
        // Add to repository
        LOG.info("Add to repository: {}", patchId);
        File targetFile = getPatchFile(patchId);
        targetFile.getParentFile().mkdirs();
        Files.copy(sourcePath, targetFile.toPath());
        Parser.writePatchSet(rootPath, patchSet);
        
        // Remove the source file when it was placed in the repository 
        if (sourcePath.startsWith(rootPath)) {
            sourcePath.toFile().delete();
        }
        
        return patchId;
    }

    @Override
    public void addPostCommand(PatchId patchId, String cmd) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(cmd, "cmd");
        LOG.info("Add post install command to: {}", patchId);
        PatchSet patchSet = getPatchSet(patchId);
        List<String> commands = new ArrayList<>(patchSet.getPostCommands());
        commands.add(cmd);
        patchSet = PatchSet.create(patchId, patchSet.getRecords(), commands);
        try {
            Parser.writePatchSet(rootPath, patchSet);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public SmartPatch getSmartPatch(PatchSet seedPatch, PatchId patchId) {

        // Derive the target patch id from the seed patch id
        if (patchId == null) {
            IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
            patchId = getLatestAvailable(seedPatch.getPatchId().getSymbolicName());
        }

        // Get the patch zip file
        File zipfile = getPatchFile(patchId);
        IllegalStateAssertion.assertTrue(zipfile.isFile(), "Cannot obtain patch file: " + zipfile);

        PatchSet targetSet = getPatchSet(patchId);
        PatchSet smartSet = PatchSet.smartSet(seedPatch, targetSet);
        return new SmartPatch(smartSet, zipfile);
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

    private File getPatchFile(PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getSymbolicName(), patchId.getVersion().toString(), patchId + ".zip")).toFile();
    }
}
