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

import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchRepository;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.ServerInstance;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;


public final class DefaultPatchTool implements PatchTool {

    private ServerInstance serverInstance;
    private PatchRepository patchRepository;
    private Path serverPath;
    private URL repoUrl;
    
	public DefaultPatchTool(Path serverPath, URL repoUrl) {
	    this.serverPath = serverPath;
	    this.repoUrl = repoUrl;
    }

    @Override
    public ServerInstance getServerInstance() {
        if (serverInstance == null) {
            serverInstance = new WildFlyServerInstance(serverPath);
        }
        return serverInstance;
    }

    @Override
    public PatchRepository getPatchRepository() {
        if (patchRepository == null) {
            if (repoUrl == null) {
                repoUrl = DefaultPatchRepository.getConfiguredUrl();
                if (repoUrl == null) {
                    try {
                        repoUrl = getServerInstance().getDefaultRepositoryPath().toUri().toURL();
                    } catch (MalformedURLException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
            patchRepository = new DefaultPatchRepository(repoUrl);
        }
        return patchRepository;
    }
    
    @Override
    public PatchSet install(PatchId patchId, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        Lock.tryLock();
        try {
            return installInternal(patchId, force);
        } finally {
            Lock.unlock();
        }
    }

    @Override
    public PatchSet update(String prefix, boolean force) throws IOException {
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
    
    private PatchSet installInternal(PatchId patchId, boolean force) throws IOException {
        
        PatchId serverId = null;
        String prefix = patchId.getName();
        for (PatchId pid : getServerInstance().queryAppliedPatches()) {
            if (pid.getName().equals(prefix)) {
                serverId = pid;
                break;
            }
        }
        
        PatchSet seedPatch = serverId != null ? getServerInstance().getPatchSet(serverId) : null;
        SmartPatch smartPatch = getPatchRepository().getSmartPatch(seedPatch, patchId);
        return getServerInstance().applySmartPatch(smartPatch, force);
    }
}
