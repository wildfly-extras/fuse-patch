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

import org.wildfly.extras.patch.Patch;
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
    private final String username;
    private final String password;

    private Server server;
    private Repository repository;
    
	public DefaultPatchTool(Path serverPath, URL repoUrl, String username, String password) {
	    this.serverPath = serverPath;
	    this.repoUrl = repoUrl;
        this.username = username;
        this.password = password;
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
                String protocol = repoUrl.getProtocol();
                if (protocol.startsWith("http")) {
                    repository = new RepositoryClient(lock, repoUrl, username, password);
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
    public Patch install(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return installInternal(patchId, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch update(String prefix, boolean force) throws IOException {
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
    public Patch uninstall(PatchId patchId) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            Patch installed = getServer().getPatch(patchId);
            PatchAssertion.assertNotNull(installed, "Patch not installed: " + patchId);
            PatchId latestId = getServer().getPatch(patchId.getName()).getPatchId();
            PatchAssertion.assertEquals(patchId, latestId, "Active package is " + latestId + ", cannot uninstall: " + patchId);
            try (SmartPatch smartPatch = SmartPatch.forUninstall(installed)) {
                return getServer().applySmartPatch(smartPatch, false);
            }
        } finally {
            lock.unlock();
        }
    }

    private Patch installInternal(PatchId patchId, boolean force) throws IOException {

        PatchId serverId = null;
        String prefix = patchId.getName();
        for (PatchId pid : getServer().queryAppliedPatches()) {
            if (pid.getName().equals(prefix)) {
                serverId = pid;
                break;
            }
        }

        Patch seedPatch = serverId != null ? getServer().getPatch(serverId) : null;
        try (SmartPatch smartPatch = getRepository().getSmartPatch(seedPatch, patchId)) {
            return getServer().applySmartPatch(smartPatch, force);
        }
    }

    @Override
    public String toString() {
        return "DefaultPatchTool[server=" + serverPath + ",repo=" + repoUrl + "]";
    }
}
