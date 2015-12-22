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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
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
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.internal.MetadataParser;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public abstract class AbstractRepository implements Repository {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRepository.class);
    
    protected final Lock lock;
    
    private final URL repositoryURL;

    public AbstractRepository(Lock lock, URL repoURL) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        IllegalArgumentAssertion.assertNotNull(repoURL, "repoURL");
        this.repositoryURL = repoURL;
        this.lock = lock;
    }

    @Override
    public URL getRepositoryURL() {
        return repositoryURL;
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        lock.tryLock();
        try {
            List<PatchId> list = new ArrayList<>(queryAvailable(prefix));
            Collections.sort(list);
            return list.isEmpty() ? null : list.get(list.size() - 1);
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
                PatchAssertion.assertNotNull(getPatch(oneoffId), "Cannot obtain target patch for: " + oneoffId);
            }

            String message = "Add " + patchId;
            if (oneoffId != null) {
                message += " patching " + oneoffId;
            }
            if (!dependencies.isEmpty()) {
                message += " with dependencies on " + dependencies;
            }
            LOG.info(message);
            
            // Collect the paths from the latest other patches
            Map<Path, Record> combinedPathsMap = new HashMap<>();
            for (PatchId auxid : queryAvailable(null)) {
                if (!patchId.getName().equals(auxid.getName())) {
                    for (Record rec : getPatch(auxid).getRecords()) {
                        combinedPathsMap.put(rec.getPath(), rec);
                    }
                }
            }

            final Path targetPath = Files.createTempFile("fptmp", ".zip");
            final File targetFile = targetPath.toFile();
            
            // Copy regular patch content to a target file
            if (oneoffId == null) {
                try (OutputStream output = new FileOutputStream(targetFile)) {
                    IOUtils.copy(dataHandler.getInputStream(), output);
                }
            }
            
            // Combine oneoff base contant with patch content to a target file
            if (oneoffId != null) {
                
                final Path workspace = Files.createTempDirectory("oneoff-workspace");
                try {
                    
                    // Unzip the base patch into the workspace
                    DataSource dataSource = getDataSource(oneoffId);
                    try (ZipInputStream zipInput = new ZipInputStream(dataSource.getInputStream())) {
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
            }
                
            // Build the patch
            Patch patch;
            try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(targetFile))) {
                Patch source = MetadataParser.buildPatchFromZip(patchId, Record.Action.INFO, zipInput);
                patch = Patch.create(metadata, source.getRecords());
            }
            
            // Assert no duplicate paths
            Set<PatchId> duplicates = new HashSet<>();
            for (Record rec : patch.getRecords()) {
                Record otherRec = combinedPathsMap.get(rec.getPath());
                if (otherRec != null) {
                    PatchId otherId = otherRec.getPatchId();
                    if (!rec.getChecksum().equals(otherRec.getChecksum())) {
                        message = "Path '" + rec.getPath() + "' already contained in: " + otherId;
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
            return addArchiveInternal(patch, new DataHandler(new FileDataSource(targetFile)));
            
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
        
        final Path targetPath = Files.createTempFile("smart-content", ".zip");
        
        // Create a temporary zip file that only contains ADD && UPD records
        DataSource dataSource = getDataSource(patchId);
        try (ZipInputStream zin = new ZipInputStream(dataSource.getInputStream())) {
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
    
    protected abstract PatchId addArchiveInternal(Patch patch, DataHandler dataHandler) throws IOException;

    protected abstract DataSource getDataSource(PatchId patchId);

    static abstract class CloseableDataSource implements DataSource, Closeable {

        private final DataSource delegate;
        
        CloseableDataSource(DataSource delegate) {
            IllegalArgumentAssertion.assertNotNull(delegate, "delegate");
            this.delegate = delegate;
        }
        
        public String getContentType() {
            return delegate.getContentType();
        }

        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        public String getName() {
            return delegate.getName();
        }

        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }
    }
}
