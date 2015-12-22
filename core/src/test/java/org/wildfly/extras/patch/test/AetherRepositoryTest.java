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
package org.wildfly.extras.patch.test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.aether.DefaultAetherFactory;
import org.wildfly.extras.patch.aether.AetherFactory;
import org.wildfly.extras.patch.utils.IOUtils;

public class AetherRepositoryTest extends AbstractRepositoryTest {

    @BeforeClass
    public static void setUp() throws Exception {
        for (int i = 0; i < repoURL.length; i++) {
            Path path = Paths.get("target/repos/AetherRepositoryTest/repo" + (i + 1));
            repoURL[i] = path.toFile().toURI().toURL();
            IOUtils.rmdirs(path);
            path.toFile().mkdirs();
        }
    }

    @Override
    boolean isRemoveSupported() {
        return false;
    }

    PatchTool getPatchTool(final URL repoURL) {
        AetherFactory factory = new DefaultAetherFactory() {
            
            Path rootPath = Paths.get(repoURL.getPath());
            {
                try {
                    IOUtils.rmdirs(rootPath);
                } catch (IOException ex) {
                    
                }
            }
            
            @Override
            public URL getRepositoryURL() {
                return repoURL;
            }
            
            @Override
            public Path getLocalRepositoryPath() {
                return rootPath.resolve("local-repo");
            }
        };
        return new PatchToolBuilder().repositoryURL(repoURL).aetherFactory(factory).build();
    }
}
