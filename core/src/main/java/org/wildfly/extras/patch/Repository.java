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

import javax.activation.DataHandler;

/**
 * A package repository.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public interface Repository {

	String SYSTEM_PROPERTY_REPOSITORY_URL = "fusepatch.repository";
	String ENV_PROPERTY_REPOSITORY_URL = "FUSEPATCH_REPOSITORY";
	
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
    Patch getPatch(PatchId patchId);

    /**
     * Add the given patch archive
     * @param fileUrl The file URL to the patch archive
     */
    PatchId addArchive(URL fileUrl) throws IOException;

    /**
     * Add the given patch archive
     * @param fileUrl The file URL to the patch archive
     * @param force Force the add operation
     */
    PatchId addArchive(URL fileUrl, boolean force) throws IOException;

    /**
     * Add the given patch archive
     * @param metadata An optional patch id if the given URL is a one-off patch
     * @param dataHandler The data handler to the patch archive
     * @param force Force the add operation
     */
    PatchId addArchive(PatchMetadata metadata, DataHandler dataHandler, boolean force) throws IOException;

    /**
     * Remove the given patch id
     */
    boolean removeArchive(PatchId removeId);

	/**
	 * Get the smart patch for the given seed.
     * @param seedPatch The patch set obtained from the server - may be null
     * @param patchId The target patch id - null for the latest
	 */
	SmartPatch getSmartPatch(Patch seedPatch, PatchId patchId);
}
