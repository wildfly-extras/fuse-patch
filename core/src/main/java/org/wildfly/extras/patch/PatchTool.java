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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.wildfly.extras.patch.utils.PatchAssertion;

/**
 * The patch tool.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public abstract class PatchTool {

    public static final Version VERSION;
    static {
        Version versionProp = null;
        InputStream input = SmartPatch.class.getResourceAsStream("version.properties");
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            String line = br.readLine();
            while (line != null) {
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("#")) {
                    versionProp = Version.parseVersion(line);
                    break;
                }
                line = br.readLine();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        PatchAssertion.assertNotNull(versionProp, "Cannot obtain fusepatch version");
        VERSION = versionProp;
    }

    /**
     * Get the server instance
     * @return The server instance
     */
    public abstract Server getServer();

    /**
     * Get the patch repository
     * @return The patch repository
     */
    public abstract Repository getRepository();

    /**
     * Install the given patch id to the server
     * @param patchId The id of the patch to install
     * @param force Whether to force patch installation
     * @return The installed patch
     * @throws java.io.IOException If an IO exception occurred
     */
    public abstract Patch install(PatchId patchId, boolean force) throws IOException;

    /**
     * Update the server for the given patch name
     * @param symbolicName The symbolic name of the patch
     * @param force Whether to force patch update
     * @return The updated patch
     * @throws java.io.IOException If an IO exception occurred
     */
    public abstract Patch update(String symbolicName, boolean force) throws IOException;

    /**
     * Uninstall the given patch id from the server
     * @param patchId The patch id to uninstall
     * @return The uninstalled patch
     * @throws java.io.IOException If an IO exception occurred
     */
    public abstract Patch uninstall(PatchId patchId) throws IOException;
}
