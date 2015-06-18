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
package com.redhat.fuse.patch;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * The patch tool.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public interface PatchTool {

    /**
     * Query the server for installed patches
     */
    List<PatchId> queryServer();

    /**
     * Query the repository for available patches
     */
    List<PatchId> queryRepository();

    /**
     * Add the given archive to the repository
     */
    PatchId add(URL archiveUrl) throws IOException;
    
    /**
     * Add a post install command for the given patch id
     */
    void addPostCommand(PatchId patchId, String cmd);
    
    /**
     * Install the given patch id to the server
     */
    PatchSet install(PatchId patchId) throws IOException;

    /**
     * Update the server for the given patch name
     */
    PatchSet update(String symbolicName) throws IOException;
}