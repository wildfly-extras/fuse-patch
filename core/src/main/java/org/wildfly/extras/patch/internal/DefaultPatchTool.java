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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;


public final class DefaultPatchTool implements PatchTool {

    private Server server;
    private Repository repository;
    private Path serverPath;
    private URL repoUrl;
    
	public DefaultPatchTool(Path serverPath, URL repoUrl) {
	    this.serverPath = serverPath;
	    this.repoUrl = repoUrl;
    }

    @Override
    public Server getServer() {
        if (server == null) {
            server = new WildFlyServer(serverPath);
        }
        return server;
    }

    @Override
    public Repository getRepository() {
        if (repository == null) {
            if (repoUrl == null) {
                repoUrl = DefaultRepository.getConfiguredUrl();
                if (repoUrl == null) {
                    try {
                        repoUrl = getServer().getDefaultRepositoryPath().toUri().toURL();
                    } catch (MalformedURLException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
            repository = new DefaultRepository(repoUrl);
        }
        return repository;
    }
    
    @Override
    public Package install(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            return installInternal(patchId, force);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public Package update(String prefix, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        Lock.tryLock();
        try {
            PatchId latestId = getRepository().getLatestAvailable(prefix);
            PatchAssertion.assertNotNull(latestId, "Cannot obtain patch id for prefix: " + prefix);
            return installInternal(latestId, force);
        } finally {
            Lock.unlock();
        }
    }
    
    @Override
    public Package uninstall(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            List<Record> records = new ArrayList<>();
            Package installed = getServer().getPackage(patchId);
            PatchAssertion.assertNotNull(installed, "Package not installed: " + patchId);
            PatchId latestId = getServer().getPackage(patchId.getName()).getPatchId();
            PatchAssertion.assertEquals(patchId, latestId, "Active package is " + latestId + ", cannot uninstall: " + patchId);
            for (Record rec : installed.getRecords()) {
                records.add(Record.create(patchId, Action.DEL, rec.getPath(), rec.getChecksum()));
            }
            SmartPatch smartPatch = new SmartPatch(Package.create(patchId, records), null);
            return getServer().applySmartPatch(smartPatch, force);
        } finally {
            Lock.unlock();
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
}
