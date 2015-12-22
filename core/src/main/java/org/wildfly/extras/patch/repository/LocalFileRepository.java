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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.internal.Main;
import org.wildfly.extras.patch.internal.MetadataParser;
import org.wildfly.extras.patch.server.WildFlyServer;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public final class LocalFileRepository extends AbstractRepository {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileRepository.class);

    private final Path rootPath;

    public LocalFileRepository(Lock lock, Path rootPath) {
        super(lock, toRepositoryUrl(rootPath));
        this.rootPath = rootPath;

        PatchAssertion.assertTrue(rootPath.toFile().isDirectory(), "Repository root does not exist: " + rootPath);
        LOG.debug("Repository location: {}", rootPath);
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
    public List<PatchId> queryAvailable(final String prefix) {
        lock.tryLock();
        try {
            return MetadataParser.queryAvailablePatches(rootPath, prefix, false);
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
    public PatchId addArchive(PatchMetadata metadata, DataHandler dataHandler, boolean force) throws IOException {
        PatchId result = super.addArchive(metadata, dataHandler, force);

        // Remove the source file when it was placed in the repository
        if (dataHandler.getDataSource() instanceof URLDataSource) {
            URL sourceURL = ((URLDataSource)dataHandler.getDataSource()).getURL();
            Path sourcePath = Paths.get(sourceURL.getPath());
            if (sourcePath.startsWith(rootPath)) {
                sourcePath.toFile().delete();
            }
        }

        return result;
    }

    @Override
    protected PatchId addArchiveInternal(Patch patch, DataHandler dataHandler) throws IOException {
        
        PatchId patchId = patch.getPatchId();
        Path targetPath = getPatchPath(patchId);
        File targetFile = targetPath.toFile();
        targetFile.getParentFile().mkdirs();
        
        try (OutputStream output = new FileOutputStream(targetFile)) {
            IOUtils.copy(dataHandler.getInputStream(), output);
        }
        
        // Write repository metadata
        MetadataParser.writePatch(rootPath, patch);

        return patchId;
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
    protected DataSource getDataSource(PatchId patchId) {
        Path patchPath = getPatchPath(patchId);
        return new FileDataSource(patchPath.toFile());
    }


    private static URL toRepositoryUrl(Path rootPath) {
        try {
            return rootPath.toUri().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    private Path getPatchPath(PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString(), patchId + ".zip"));
    }

    @Override
    public String toString() {
        return "LocalFileRepository[rootPath=" + rootPath + "]";
    }
}
