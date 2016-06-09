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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * A server instance.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public interface Server {

    PatchId SERVER_ID = PatchId.fromString("server");
    
	/**
	 * Get the server home path
	 * @return The path to the server home
	 */
	File getServerHome();
	
	/**
	 * Get the default repository URL
	 * @return The URL for the default repository
	 */
    URL getDefaultRepositoryURL();
    
    /**
     * Get the audit log
	 * @return A list containing the audit log content
     */
    List<String> getAuditLog();
    
	/**
	 * Query the list of applied packages
	 * @return A list of applied patches
	 */
	List<PatchId> queryAppliedPatches();

	/**
	 * Query managed server paths
	 * @param pathsPattern The path pattern to query for
	 * @return A list of managed server paths
	 */
    List<ManagedPath> queryManagedPaths(String pathsPattern);
    
    /**
     * Get the applied package for a given prefix
	 * @param prefix The patch prefix
     * @return package or null
     */
	Patch getPatch(String prefix);

    /**
     * Get the applied package for the given id
	 * @param patchId The patch id
	 * @return The patch
     */
    Patch getPatch(PatchId patchId);
    
	/**
	 * Apply a smart patch and return the result
	 * @param smartPatch The patch to apply
	 * @param force Whether to force application of the patch
	 * @return The patched that was applied
	 * @throws java.io.IOException If an IO exception occurred
	 */
	Patch applySmartPatch(SmartPatch smartPatch, boolean force) throws IOException;

	/**
	 * Apply cleanup tasks to a server
	 */
	void cleanUp();
}
