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
package com.redhat.fuse.patch;

import java.net.URL;
import java.nio.file.Path;

import com.redhat.fuse.patch.internal.DefaultPatchTool;


/**
 * The default {@link PatchTool} builder.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public final class PatchToolBuilder {

    private Path serverPath;
    private URL repoUrl;

    public PatchToolBuilder serverPath(Path serverPath) {
        this.serverPath = serverPath;
        return this;
    }


    public PatchToolBuilder repositoryUrl(URL repoUrl) {
        this.repoUrl = repoUrl;
        return this;
    }

    public PatchTool build() {
        return new DefaultPatchTool(serverPath, repoUrl);
    }
}