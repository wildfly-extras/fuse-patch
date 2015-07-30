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
import java.net.URI;
import java.nio.file.Path;

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.ServerInstance;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;


public final class DefaultPatchTool implements PatchTool {

    private ServerInstance serverInstance;
    private Repository patchRepository;
    private Path serverPath;
    private URI repoUri;
    
	public DefaultPatchTool(Path serverPath, URI repoUri) {
	    this.serverPath = serverPath;
	    this.repoUri = repoUri;
    }

    @Override
    public ServerInstance getServerInstance() {
        if (serverInstance == null) {
            serverInstance = new WildFlyServerInstance(serverPath);
        }
        return serverInstance;
    }

    @Override
    public Repository getPatchRepository() {
        if (patchRepository == null) {
            if (repoUri == null) {
                repoUri = DefaultPatchRepository.getConfiguredUrl();
                if (repoUri == null) {
                    repoUri = getServerInstance().getDefaultRepositoryPath().toUri();
                }
            }
            patchRepository = new DefaultPatchRepository(repoUri);
        }
        return patchRepository;
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
            PatchId latestId = getPatchRepository().getLatestAvailable(prefix);
            PatchAssertion.assertNotNull(latestId, "Cannot obtain patch id for prefix: " + prefix);
            return installInternal(latestId, force);
        } finally {
            Lock.unlock();
        }
    }
    
    private Package installInternal(PatchId patchId, boolean force) throws IOException {
        
        PatchId serverId = null;
        String prefix = patchId.getName();
        for (PatchId pid : getServerInstance().queryAppliedPatches()) {
            if (pid.getName().equals(prefix)) {
                serverId = pid;
                break;
            }
        }
        
        Package seedPatch = serverId != null ? getServerInstance().getPackage(serverId) : null;
        SmartPatch smartPatch = getPatchRepository().getSmartPatch(seedPatch, patchId);
        return getServerInstance().applySmartPatch(smartPatch, force);
    }
}
