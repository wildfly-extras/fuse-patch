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
package org.wildfly.extras.patch;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * A patch repository.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public interface PatchRepository {

    /**
     * Get the list of available patches
     * @param prefix The patch name prefix - null for all patches
     */
    List<PatchId> queryAvailable(String prefix);

    /**
     * Get the latest available patche for the given prefix
     * @param prefix The mandatory patch name prefix
     */
    PatchId getLatestAvailable(String prefix);

    /**
     * Get the patch set for the given id
     */
    PatchSet getPatchSet(PatchId patchId);
    
    /**
     * Add the given patch archive
     */
    PatchId addArchive(URL fileUrl) throws IOException;
    
    /**
     * Add a post-install command for the given patch id
     */
    void addPostCommand(PatchId patchId, String cmd);
    
	/**
	 * Get the smart patch for the given seed.
     * @param seedPatch The patch set obtained from the server - may be null
     * @param patchId The target patch id - null for the latest
	 */
	SmartPatch getSmartPatch(PatchSet seedPatch, PatchId patchId);

}
