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

/**
 * The patch tool.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public interface PatchTool {

    /**
     * Get the server instance
     */
    Server getServer();
    
    /**
     * Get the patch repository
     */
    Repository getRepository();
    
    /**
     * Install the given patch id to the server
     */
    Package install(PatchId patchId, boolean force) throws IOException;

    /**
     * Update the server for the given patch name
     */
    Package update(String symbolicName, boolean force) throws IOException;

    /**
     * Uninstall the given patch id from the server
     */
    Package uninstall(PatchId patchId, boolean force) throws IOException;
}