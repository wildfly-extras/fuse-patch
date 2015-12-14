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
package org.wildfly.extras.patch.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.MetadataParser;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.Main;
import org.wildfly.extras.patch.server.WildFlyServer;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public final class LocalFileRepository implements Repository {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileRepository.class);

    private final Lock lock;
    private final URL baseURL;
    private final Path rootPath;

    public LocalFileRepository(Lock lock, URL repoUrl) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        IllegalArgumentAssertion.assertNotNull(repoUrl, "repoUrl");
        this.lock = lock;
        this.baseURL = repoUrl;

        Path path = getAbsolutePath(repoUrl);
        PatchAssertion.assertTrue(path.toFile().isDirectory(), "Repository root does not exist: " + path);
        LOG.debug("Repository location: {}", path);
        this.rootPath = path;
    }

    public static URL getDefaultRepositoryURL() {
        String repoSpec = System.getProperty(Repository.SYSTEM_PROPERTY_REPOSITORY_URL);
        if (repoSpec == null) {
            repoSpec = System.getenv(Repository.ENV_PROPERTY_REPOSITORY_URL);
        }
        if (repoSpec == null) {
            CodeSource codeSource = Main.class.getProtectionDomain().getCodeSource();
            URL codeLocation = codeSource != null ? codeSource.getLocation() : null;
            if (codeLocation != null) {
                String modulePath = "modules/system/layers/" + WildFlyServer.MODULE_LAYER + "/org/wildfly/extras/patch";
                if (codeLocation.getPath().contains(modulePath)) {
                    Path jbossHome = Paths.get(codeLocation.getPath().substring(0, codeLocation.getPath().indexOf(modulePath)));
                    Path repositoryPath = jbossHome.resolve("fusepatch").resolve("repository");
                    repoSpec = repositoryPath.toString();
                }
            }
        }
        URL repoUrl = null;
        if (repoSpec != null) {
            try {
            	repoUrl = new URL(repoSpec);
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return repoUrl;
    }
    
    @Override
    public URL getBaseURL() {
        return baseURL;
    }

    @Override
    public List<PatchId> queryAvailable(final String prefix) {
        lock.tryLock();
        try {
            return MetadataParser.queryAvailablePatches(rootPath, prefix, false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        lock.tryLock();
        try {
            List<PatchId> list = MetadataParser.queryAvailablePatches(rootPath, prefix, true);
            return list.isEmpty() ? null : list.get(0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch getPatch(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return MetadataParser.readPatch(rootPath, patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        lock.tryLock();
        try {
            PatchId patchId = PatchId.fromURL(fileUrl);
            DataHandler dataHandler = new DataHandler(new URLDataSource(fileUrl));
            PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).build();
            return addArchive(metadata, dataHandler, false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl, boolean force) throws IOException {
        lock.tryLock();
        try {
            PatchId patchId = PatchId.fromURL(fileUrl);
            DataHandler dataHandler = new DataHandler(new URLDataSource(fileUrl));
            PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).build();
            return addArchive(metadata, dataHandler, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(PatchMetadata metadata, DataHandler dataHandler, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        IllegalArgumentAssertion.assertNotNull(dataHandler, "dataHandler");
        
        // Unwrap the package metadata
        PatchId patchId = metadata.getPatchId();
        PatchId oneoffId = metadata.getOneoffId();
        Set<PatchId> dependencies = metadata.getDependencies();
        
        lock.tryLock();
        try {
            // Cannot add already existing archive
            if (queryAvailable(null).contains(patchId)) {
                LOG.warn("Repository already contains {}", patchId);
                return patchId;
            }

            // Verify one-off id
            if (oneoffId != null) {
                File metadataFile = MetadataParser.getMetadataFile(rootPath, oneoffId);
                PatchAssertion.assertTrue(metadataFile.isFile(), "Cannot obtain target patch for: " + oneoffId);
            }

            // Collect the paths from the latest other patch sets
            Map<Path, Record> combinedPathsMap = new HashMap<>();
            for (PatchId auxid : MetadataParser.queryAvailablePatches(rootPath, null, false)) {
                if (!patchId.getName().equals(auxid.getName())) {
                    for (Record rec : getPatch(auxid).getRecords()) {
                        combinedPathsMap.put(rec.getPath(), rec);
                    }
                }
            }

            // Add to repository
            Path targetPath = getPatchPath(patchId);
            File targetFile = targetPath.toFile();
            targetFile.getParentFile().mkdirs();
            if (oneoffId != null) {
                final Path basePath = getPatchPath(oneoffId);
                final Path workspace = targetPath.getParent().resolve("workspace");
                try {
                    // Unzip the base patch into the workspace
                    try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(basePath.toFile()))) {
                        byte[] buffer = new byte[64 * 1024];
                        ZipEntry entry = zipInput.getNextEntry();
                        while (entry != null) {
                            if (!entry.isDirectory()) {
                                String name = entry.getName();
                                File entryFile = workspace.resolve(Paths.get(name)).toFile();
                                entryFile.getParentFile().mkdirs();
                                try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                                    int read = zipInput.read(buffer);
                                    while (read > 0) {
                                        fos.write(buffer, 0, read);
                                        read = zipInput.read(buffer);
                                    }
                                }
                            }
                            entry = zipInput.getNextEntry();
                        }
                    }

                    // Unzip the one-off patch into the workspace
                    try (ZipInputStream zipInput = new ZipInputStream(dataHandler.getInputStream())) {
                        byte[] buffer = new byte[64 * 1024];
                        ZipEntry entry = zipInput.getNextEntry();
                        while (entry != null) {
                            if (!entry.isDirectory()) {
                                String name = entry.getName();
                                File entryFile = workspace.resolve(Paths.get(name)).toFile();
                                entryFile.getParentFile().mkdirs();
                                try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                                    int read = zipInput.read(buffer);
                                    while (read > 0) {
                                        fos.write(buffer, 0, read);
                                        read = zipInput.read(buffer);
                                    }
                                }
                            }
                            entry = zipInput.getNextEntry();
                        }
                    }

                    // Create the target zip file
                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetFile))) {
                        Files.walkFileTree(workspace, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                                Path relpath = workspace.relativize(path);
                                zos.putNextEntry(new ZipEntry(relpath.toString()));
                                try (FileInputStream fis = new FileInputStream(path.toFile())) {
                                    IOUtils.copy(fis, zos);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                } finally {
                    IOUtils.rmdirs(workspace);
                }
            } else {
                try (OutputStream output = new FileOutputStream(targetFile)) {
                    IOUtils.copy(dataHandler.getInputStream(), output);
                }
            }
            
            // Remove the source file when it was placed in the repository
            if (dataHandler.getDataSource() instanceof URLDataSource) {
                URL sourceURL = ((URLDataSource)dataHandler.getDataSource()).getURL();
                Path sourcePath = Paths.get(sourceURL.getPath());
                if (sourcePath.startsWith(rootPath)) {
                    sourcePath.toFile().delete();
                }
            }

            // Build the patch set
            Patch patchSet;
            try {
                try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(targetFile))) {
                    Patch sourceSet = MetadataParser.buildPatchFromZip(patchId, Record.Action.INFO, zipInput);
                    patchSet = Patch.create(metadata, sourceSet.getRecords());
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
            } catch (IOException ex) {
                targetFile.delete();
                throw ex;
            } catch (RuntimeException rte) {
                targetFile.delete();
                throw rte;
            }

            // Write repository metadata
            MetadataParser.writePatch(rootPath, patchSet);

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
            lock.unlock();
        }
    }

    @Override
    public boolean removeArchive(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            File patchdir = MetadataParser.getMetadataDirectory(rootPath, patchId);
            PatchAssertion.assertTrue(patchdir.isDirectory(), "Archive does not exist: " + patchId);
            IOUtils.rmdirs(patchdir.toPath());
            LOG.info("Removed {}", patchId);
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(Patch seedPatch, PatchId patchId) {
        lock.tryLock();
        try {
            // Derive the target patch id from the seed patch id
            if (patchId == null) {
                IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
                patchId = getLatestAvailable(seedPatch.getPatchId().getName());
            }
            Patch targetSet = getPatch(patchId);
            PatchAssertion.assertNotNull(targetSet, "Repository does not contain package: " + patchId);
            Patch smartSet = Patch.smartDelta(seedPatch, targetSet);
            CloseableDataSource dataSource = getSmartDataSource(smartSet, patchId);
            DataHandler dataHandler = new DataHandler(dataSource);
            return SmartPatch.forInstall(smartSet, dataHandler);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    private CloseableDataSource getSmartDataSource(Patch smartSet, PatchId patchId) throws IOException {
        
        final Path sourcePath = getPatchPath(patchId);
        final Path workspace = sourcePath.getParent().resolve("workspace");
        workspace.toFile().mkdirs();
        
        final Path targetPath = Files.createTempFile(workspace, "smart-content", ".zip");
        
        // Create a temporary zip file that only contains ADD && UPD records
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(sourcePath.toFile()))) {
            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(targetPath.toFile()))) {
                byte[] buffer = new byte[64 * 1024];
                ZipEntry entry = zin.getNextEntry();
                while (entry != null) {
                    Record rec = smartSet.getRecord(Paths.get(entry.getName()));
                    if (!entry.isDirectory() && rec != null && (rec.getAction() == Action.ADD || rec.getAction() == Action.UPD)) {
                        zout.putNextEntry(new ZipEntry(entry.getName()));
                        int read = zin.read(buffer);
                        while (read > 0) {
                            zout.write(buffer, 0, read);
                            read = zin.read(buffer);
                        }
                    }
                    entry = zin.getNextEntry();
                }
            }
        }
        
        DataSource datasource = new FileDataSource(targetPath.toFile());
        return new CloseableDataSource(datasource) {
            @Override
            public void close() throws IOException {
                targetPath.toFile().delete();
            }
        };
    }

    private Path getPatchPath(PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString(), patchId + ".zip"));
    }

    private Path getAbsolutePath(URL url) {
        IllegalArgumentAssertion.assertTrue("file".equals(url.getProtocol()), "Unsupported protocol: " + url);
        try {
            return new File(URLDecoder.decode(url.getPath(), "UTF-8")).getAbsoluteFile().toPath();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public String toString() {
        return "DefaultRepository[rootPath=" + rootPath + "]";
    }
}
