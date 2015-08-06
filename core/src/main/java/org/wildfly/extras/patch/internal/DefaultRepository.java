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
import java.io.FileInputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

final class DefaultRepository implements Repository {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRepository.class);
    
    private final Path rootPath;

    DefaultRepository(URL repoUrl) {
        if (repoUrl == null) {
            repoUrl = getConfiguredUrl();
        }
        IllegalStateAssertion.assertNotNull(repoUrl, "Cannot obtain repository URL");

        Path path = getAbsolutePath(repoUrl);
        PatchAssertion.assertTrue(path.toFile().isDirectory(), "Repository root does not exist: " + path);
        LOG.debug("Repository location: {}", path);
        this.rootPath = path;
    }

    @Override
    public List<PatchId> queryAvailable(final String prefix) {
        Lock.tryLock();
        try {
            return Parser.queryAppliedPackages(rootPath, prefix, false);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        Lock.tryLock();
        try {
            List<PatchId> list = Parser.queryAppliedPackages(rootPath, prefix, true);
            return list.isEmpty() ? null : list.get(0);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public Package getPackage(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            return Parser.readPackage(rootPath, patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        return addArchive(fileUrl, null, Collections.<PatchId>emptySet(), false);
    }
    
    @Override
    public PatchId addArchive(URL fileUrl, PatchId oneoffId) throws IOException {
        return addArchive(fileUrl, oneoffId, Collections.<PatchId>emptySet(), false);
    }
    
    @Override
    public PatchId addArchive(URL fileUrl, PatchId oneoffId, Set<PatchId> dependencies, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(fileUrl, "fileUrl");
        IllegalArgumentAssertion.assertTrue(fileUrl.getPath().endsWith(".zip"), "Unsupported file extension: " + fileUrl);
        Lock.tryLock();
        try {
            Path sourcePath = getAbsolutePath(fileUrl);
            PatchId patchId = PatchId.fromURL(fileUrl);
            PatchAssertion.assertFalse(queryAvailable(null).contains(patchId), "Repository already contains " + patchId);

            // Verify one-off id
            if (oneoffId != null) {
                File metadataFile = Parser.getMetadataFile(rootPath, oneoffId);
                PatchAssertion.assertTrue(metadataFile.isFile(), "Cannot obtain target patch for: " + oneoffId);
            }
            
            // Collect the paths from the latest other patch sets
            Map<Path, Record> combinedPathsMap = new HashMap<>();
            for (PatchId auxid : Parser.queryAppliedPackages(rootPath, null, false)) {
                if (!patchId.getName().equals(auxid.getName())) {
                    for (Record rec : getPackage(auxid).getRecords()) {
                        combinedPathsMap.put(rec.getPath(), rec);
                    }
                }
            }
            
            // Build the patch set
            Package patchSet;
            try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(sourcePath.toFile()))) {
                if (oneoffId != null) {
                    Package oneoffSet = getPackage(oneoffId);
                    Map<Path, Record> records = new HashMap<>();
                    for (Record rec : oneoffSet.getRecords()) {
                        records.put(rec.getPath(), rec);
                    }
                    Package sourceSet = Parser.buildPackageFromZip(patchId, Record.Action.INFO, zipInput);
                    for (Record rec : sourceSet.getRecords()) {
                        records.put(rec.getPath(), rec);
                    }
                    Set<PatchId> depids = new LinkedHashSet<>(dependencies);
                    depids.add(oneoffId);
                    patchSet = Package.create(patchId, records.values(), depids);
                } else {
                    Package sourceSet = Parser.buildPackageFromZip(patchId, Record.Action.INFO, zipInput);
                    patchSet = Package.create(patchId, sourceSet.getRecords(), dependencies);
                }
            }
            
            // Assert no duplicate paths
            Set<PatchId> duplicates = new HashSet<>();
            for (Record rec : patchSet.getRecords()) {
                Record otherRec = combinedPathsMap.get(rec.getPath());
                if (otherRec != null) {
                    PatchId otherId = otherRec.getPatchId();
                    if (!rec.getChecksum().equals(otherRec.getChecksum())) {
                        String message = "Path '" + rec.getPath() + "' already contained in: " + otherId;
                        if (force) {
                            LOG.warn(message);
                        } else {
                            LOG.error(message);
                        }
                        duplicates.add(otherId);
                    }
                }
            }
            PatchAssertion.assertTrue(force || duplicates.isEmpty(), "Cannot add " + patchId + " because of duplicate paths in " + duplicates);
            
            // Add to repository
            File targetFile = getPackagePath(patchId).toFile();
            targetFile.getParentFile().mkdirs();
            Files.copy(sourcePath, targetFile.toPath());
            Parser.writePackage(rootPath, patchSet);
            
            // Remove the source file when it was placed in the repository 
            if (sourcePath.startsWith(rootPath)) {
                sourcePath.toFile().delete();
            }
            
            String message = "Added " + patchId;
            if (oneoffId != null) {
                message += " patching " + oneoffId;
            }
            if (!dependencies.isEmpty()) {
                message += " with dependencies on " + dependencies;
            }
            LOG.info(message);
            
            return patchId;
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public boolean removeArchive(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            File patchdir = Parser.getMetadataDirectory(rootPath, patchId);
            PatchAssertion.assertTrue(patchdir.isDirectory(), "Archive does not exist: " + patchId);
            IOUtils.rmdirs(patchdir.toPath());
            LOG.info("Removed " + patchId);
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
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
            Package patchSet = getPackage(patchId);
            List<String> commands = new ArrayList<>(patchSet.getPostCommands());
            commands.add(commandString(cmdarr));
            patchSet = Package.create(patchId, patchSet.getRecords(), commands);
            try {
                Parser.writePackage(rootPath, patchSet);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            LOG.info("Added post install command to " + patchId);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(Package seedPatch, PatchId patchId) {
        Lock.tryLock();
        try {
            // Derive the target patch id from the seed patch id
            if (patchId == null) {
                IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
                patchId = getLatestAvailable(seedPatch.getPatchId().getName());
            }
            Package targetSet = getPackage(patchId);
            PatchAssertion.assertNotNull(targetSet, "Repository does not contain package: " + patchId);
            Package smartSet = Package.smartSet(seedPatch, targetSet);
            return SmartPatch.forInstall(smartSet, getPackageURL(patchId));
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

    private Path getPackagePath(PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString(), patchId + ".zip"));
    }

    private URL getPackageURL(PatchId patchId) {
        try {
            return getPackagePath(patchId).toFile().toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Path getAbsolutePath(URL url) {
        IllegalArgumentAssertion.assertTrue("file".equals(url.getProtocol()), "Unsupported protocol: " + url);
        return new File(url.getPath()).getAbsoluteFile().toPath();
    }

    @Override
    public String toString() {
        return "DefaultRepository[rootPath=" + rootPath + "]";
    }
}
