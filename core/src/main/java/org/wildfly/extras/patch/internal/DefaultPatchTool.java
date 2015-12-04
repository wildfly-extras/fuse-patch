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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.namespace.QName;

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.repository.RepositoryClient;
import org.wildfly.extras.patch.server.WildFlyServer;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;


public final class DefaultPatchTool extends PatchTool {

    // All default patch tool instances share this lock
    private static ReentrantLock lock = new ReentrantLock();
    
    private final Path serverPath;
    private final URL repoUrl;
    private final QName serviceName;

    private Server server;
    private Repository repository;
    
	public DefaultPatchTool(Path serverPath, QName serviceName, URL repoUrl) {
	    this.serverPath = serverPath;
	    this.repoUrl = repoUrl;
        this.serviceName = serviceName;
    }

    @Override
    public Server getServer() {
        lock.tryLock();
        try {
            if (server == null) {
                server = new WildFlyServer(lock, serverPath);
            }
            return server;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Repository getRepository() {
        lock.tryLock();
        try {
            if (repository == null) {
                if (serviceName != null) {
                    repository = new RepositoryClient(lock, serviceName, repoUrl);
                } else {
                    repository = new LocalFileRepository(lock, repoUrl);
                }
            }
            return repository;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Package install(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return installInternal(patchId, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Package update(String prefix, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        lock.tryLock();
        try {
            PatchId latestId = getRepository().getLatestAvailable(prefix);
            PatchAssertion.assertNotNull(latestId, "Cannot obtain patch id for prefix: " + prefix);
            return installInternal(latestId, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Package uninstall(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            Package installed = getServer().getPackage(patchId);
            PatchAssertion.assertNotNull(installed, "Package not installed: " + patchId);
            PatchId latestId = getServer().getPackage(patchId.getName()).getPatchId();
            PatchAssertion.assertEquals(patchId, latestId, "Active package is " + latestId + ", cannot uninstall: " + patchId);
            SmartPatch smartPatch = SmartPatch.forUninstall(installed);
            return getServer().applySmartPatch(smartPatch, force);
        } finally {
            lock.unlock();
        }
    }

    private Package installInternal(PatchId patchId, boolean force) throws IOException {

        PatchId serverId = null;
        String prefix = patchId.getName();
        for (PatchId pid : getServer().queryAppliedPackages()) {
            if (pid.getName().equals(prefix)) {
                serverId = pid;
                break;
            }
        }

        Package seedPatch = serverId != null ? getServer().getPackage(serverId) : null;
        SmartPatch smartPatch = getRepository().getSmartPatch(seedPatch, patchId);
        return getServer().applySmartPatch(smartPatch, force);
    }

    @Override
    public String toString() {
        return "DefaultPatchTool[server=" + serverPath + ",repo=" + repoUrl + "]";
    }
}
