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
import java.util.Set;

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
    List<Identity> queryAvailable(String prefix);

    /**
     * Get the latest available patche for the given prefix
     * @param prefix The mandatory patch name prefix
     */
    Identity getLatestAvailable(String prefix);

    /**
     * Get the patch set for the given id
     */
    Package getPatchSet(Identity patchId);
    
    /**
     * Add the given patch archive
     * @param fileUrl The file URL to the patch archive
     */
    Identity addArchive(URL fileUrl) throws IOException;
    
    /**
     * Add the given patch archive
     * @param fileUrl The file URL to the patch archive
     * @param oneoffId An optional patch id if the given URL is a one-off patch
     */
    Identity addArchive(URL fileUrl, Identity oneoffId) throws IOException;
    
    /**
     * Add the given patch archive
     * @param fileUrl The file URL to the patch archive
     * @param oneoffId An optional patch id if the given URL is a one-off patch
     * @param dependencies An optional set of patch dependencies
     */
    Identity addArchive(URL fileUrl, Identity oneoffId, Set<Identity> dependencies) throws IOException;
    
    /**
     * Add a post-install command for the given patch id
     */
    void addPostCommand(Identity patchId, String[] cmdarr);
    
	/**
	 * Get the smart patch for the given seed.
     * @param seedPatch The patch set obtained from the server - may be null
     * @param patchId The target patch id - null for the latest
	 */
	SmartPatch getSmartPatch(Package seedPatch, Identity patchId);

}
