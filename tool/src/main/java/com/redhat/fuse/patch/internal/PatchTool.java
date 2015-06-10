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
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchPool;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.ServerInstance;
import com.redhat.fuse.patch.SmartPatch;


final class PatchTool {

	void queryServer(Path targetPath) {
		ServerInstance server = new WildFlyServerInstance(targetPath);
		printPatches(server.queryAppliedPatches());
	}

	void queryPool(URL poolUrl) {
		PatchPool pool = new DefaultPatchPool(poolUrl);
		printPatches(pool.queryAvailablePatches(null));
	}

	void updateServer(Path targetPath, URL poolUrl) throws IOException {
		ServerInstance server = new WildFlyServerInstance(targetPath);
		PatchPool pool = new DefaultPatchPool(poolUrl);
		
		PatchSet latest = server.getLatestPatch();
		SmartPatch smartPatch = pool.getSmartPatch(latest, null);
		server.applySmartPatch(smartPatch);
	}

	private void printPatches(List<PatchId> patches) {
		for (PatchId patchId : patches) {
			System.out.println(patchId);
		}
	}
}
