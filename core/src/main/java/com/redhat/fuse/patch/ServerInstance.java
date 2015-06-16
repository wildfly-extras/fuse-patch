/*
 * #%L
 * Gravia :: Resource
 * %%
 * Copyright (C) 2010 - 2014 JBoss by Red Hat
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
package com.redhat.fuse.patch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A server instance.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public interface ServerInstance {

	/**
	 * Get the server home path
	 */
	Path getServerHome();
	
	/**
	 * Get the list of applied patches
	 */
	List<PatchId> queryAppliedPatches();

    /**
     * Get an applied patch set
     */
    PatchSet getAppliedPatchSet(PatchId patchId);
    
    /**
     * Get the latest applied patch
     */
    PatchSet getLatestPatch();

	/**
	 * Apply a smart patch and return the result
	 */
	PatchSet applySmartPatch(SmartPatch smartPatch) throws IOException;
}
