/*
 * #%L
 * Fuse Patch :: Parser
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
package com.redhat.fuse.patch.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchRepository;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.PatchTool;
import com.redhat.fuse.patch.ServerInstance;
import com.redhat.fuse.patch.SmartPatch;


public final class DefaultPatchTool implements PatchTool {

    private final ServerInstance server;
    private final PatchRepository repository;
    
	public DefaultPatchTool(Path serverPath, URL repoUrl) {
        server = new WildFlyServerInstance(serverPath);
        if (repoUrl == null) {
            Path serverHome = server.getServerHome();
            try {
                repoUrl = serverHome.resolve(Paths.get("fusepatch", "repository")).toUri().toURL();
            } catch (MalformedURLException ex) {
                // ignore
            }
        }
        repository = new DefaultPatchRepository(repoUrl);
    }

    @Override
    public List<PatchId> queryServer() {
		return server.queryAppliedPatches();
	}

	@Override
    public List<PatchId> queryRepository() {
		return repository.queryAvailablePatches(null);
	}

    @Override
    public void install(PatchId patchId) throws IOException {
        PatchSet latest = server.getLatestPatch();
        SmartPatch smartPatch = repository.getSmartPatch(latest, patchId);
        server.applySmartPatch(smartPatch);
    }

    @Override
    public void update() throws IOException {
        PatchSet latest = server.getLatestPatch();
        SmartPatch smartPatch = repository.getSmartPatch(latest, null);
        server.applySmartPatch(smartPatch);
    }
}
