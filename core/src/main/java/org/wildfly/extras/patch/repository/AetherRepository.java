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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.version.Version;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.aether.AetherFactory;
import org.wildfly.extras.patch.internal.MetadataParser;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

public class AetherRepository extends AbstractRepository {

    static final String GROUP_ID = "fusepatch";

    private final AetherFactory factory;

    public AetherRepository(Lock lock, AetherFactory factory) {
        super(lock, factory.getRepositoryURL());
        IllegalArgumentAssertion.assertNotNull(factory, "factory");
        this.factory = factory;
    }

    @Override
    public List<PatchId> queryAvailable(String prefix) {
        lock.tryLock();
        try {
            RepositorySystem system = factory.getRepositorySystem();
            RepositorySystemSession session = factory.newRepositorySystemSession();
            RemoteRepository target = factory.getRemoteRepository();
            
            Set<String> names = new HashSet<String>();
            if (prefix == null) {
                URL repoURL = new URL(target.getUrl());
                IllegalStateAssertion.assertEquals("file", repoURL.getProtocol(), "Cannot query remote repository");
                File repoPath = new File(repoURL.toURI());
                File groupPath = new File(repoPath, GROUP_ID);
                if (groupPath.isDirectory()) {
                    names.addAll(Arrays.asList(groupPath.list()));
                }
            } else {
                names.add(prefix);
            }
            List<PatchId> result = new ArrayList<PatchId>();
            for (String name : names) {
                Artifact artifact = new DefaultArtifact(GROUP_ID, name, "", "metadata", "[0,)");

                VersionRangeRequest rangeRequest = new VersionRangeRequest();
                rangeRequest.setArtifact(artifact);
                rangeRequest.setRepositories(Collections.singletonList(target));

                VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);
                for (Version version : rangeResult.getVersions()) {
                    result.add(PatchId.create(name, version.toString()));
                }
            }
            Collections.sort(result);
            Collections.reverse(result);
            return Collections.unmodifiableList(result);
        } catch (VersionRangeResolutionException ex) {
            throw new IllegalStateException(ex);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch getPatch(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            RepositorySystem system = factory.getRepositorySystem();
            RepositorySystemSession session = factory.newRepositorySystemSession();
            RemoteRepository target = factory.getRemoteRepository();
            
            Artifact artifact = new DefaultArtifact(GROUP_ID, patchId.getName(), "", "metadata", patchId.getVersion().toString());

            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(artifact);
            artifactRequest.setRepositories(Collections.singletonList(target));

            ArtifactResult artifactResult;
            try {
                artifactResult = system.resolveArtifact(session, artifactRequest);
            } catch (ArtifactResolutionException ex) {
                return null;
            }
            
            artifact = artifactResult.getArtifact();
            return MetadataParser.readPatch(artifact.getFile());

        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeArchive(PatchId removeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected PatchId addArchiveInternal(Patch patch, DataHandler dataHandler) throws IOException {

        PatchId patchId = patch.getPatchId();

        File tmpFile = File.createTempFile("fptmp", ".metadata");
        try {
            RepositorySystem system = factory.getRepositorySystem();
            RepositorySystemSession session = factory.newRepositorySystemSession();
            RemoteRepository target = factory.getRemoteRepository();
            
            Artifact zipArtifact = new DefaultArtifact(GROUP_ID, patchId.getName(), "", "zip", patchId.getVersion().toString());
            DataSource dataSource = dataHandler.getDataSource();
            if (dataSource instanceof FileDataSource) {
                FileDataSource fileds = (FileDataSource) dataSource;
                zipArtifact = zipArtifact.setFile(fileds.getFile());
            }

            FileOutputStream fos = new FileOutputStream(tmpFile);
            try {
                MetadataParser.writePatch(patch, fos, true);
            } finally {
                fos.close();
            }

            Artifact metadataArtifact = new SubArtifact(zipArtifact, "", "metadata");
            metadataArtifact = metadataArtifact.setFile(tmpFile);

            DeployRequest deployRequest = new DeployRequest();
            deployRequest.addArtifact(zipArtifact).addArtifact(metadataArtifact);
            deployRequest.setRepository(target);

            DeployResult result;
            try {
                result = system.deploy(session, deployRequest);
            } catch (DeploymentException ex) {
                throw new IOException(ex);
            }
            List<Artifact> artifacts = new ArrayList<Artifact>(result.getArtifacts());
            IllegalStateAssertion.assertEquals(2, artifacts.size(), "Not all artifacts deployed: " + result);

            return patchId;
        } finally {
            tmpFile.delete();
        }
    }

    @Override
    protected DataSource getDataSource(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        
        RepositorySystem system = factory.getRepositorySystem();
        RepositorySystemSession session = factory.newRepositorySystemSession();
        RemoteRepository target = factory.getRemoteRepository();
        
        Artifact artifact = new DefaultArtifact(GROUP_ID, patchId.getName(), "", "zip", patchId.getVersion().toString());

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(Collections.singletonList(target));

        try {
            ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
            artifact = artifactResult.getArtifact();
            return new FileDataSource(artifact.getFile());
        } catch (ArtifactResolutionException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
