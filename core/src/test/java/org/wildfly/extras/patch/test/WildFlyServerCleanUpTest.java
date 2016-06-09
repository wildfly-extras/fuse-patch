/*
 * #%L
 * Fuse Patch :: Core
 * %%
 * Copyright (C) 2016 Private
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

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.utils.IOUtils;

public class WildFlyServerCleanUpTest {

    private static File SERVER_PATH = new File("target/my-server");
    private static File MODULE_BASE_PATH = new File("target/my-server/modules/system/layers/fuse/org/wildfly/extras");

    @Before
    public void setUp() throws Exception {
        new File(SERVER_PATH, "fusepatch/repository").mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        IOUtils.rmdirs(SERVER_PATH);
    }

    @Test
    public void testServerCleanUp() throws Exception {
        PatchToolBuilder builder = new PatchToolBuilder();
        PatchTool patchTool = builder.serverPath(SERVER_PATH).build();
        Server server = patchTool.getServer();

        writeDummyModule(new File(MODULE_BASE_PATH, "patch/main"), "fuse-patch-core", true);
        writeDummyModule(new File(MODULE_BASE_PATH, "config/main"), "fuse-patch-config", true);

        File patchModule = new File(MODULE_BASE_PATH, "patch/main");
        File configModule = new File(MODULE_BASE_PATH, "config/main");

        // Assert files present before cleanup
        assertExpectedFileCount(patchModule, 5);
        assertExpectedFileCount(configModule, 5);

        server.cleanUp();

        // Make sure the correct jars and the module.xml remain
        assertExpectedFileCount(patchModule, 3);
        Assert.assertTrue(new File(patchModule, "fuse-patch-core-1.0.1.jar").exists());
        Assert.assertTrue(new File(patchModule, "fuse-patch-core-test-1.0.1.jar").exists());
        Assert.assertTrue(new File(patchModule, "module.xml").exists());

        assertExpectedFileCount(configModule, 3);
        Assert.assertTrue(new File(configModule, "fuse-patch-config-1.0.1.jar").exists());
        Assert.assertTrue(new File(configModule, "fuse-patch-config-test-1.0.1.jar").exists());
        Assert.assertTrue(new File(configModule, "module.xml").exists());
    }

    @Test
    public void testServerCleanUpNoOp() throws Exception {
        PatchToolBuilder builder = new PatchToolBuilder();
        PatchTool patchTool = builder.serverPath(SERVER_PATH).build();
        Server server = patchTool.getServer();

        writeDummyModule(new File(MODULE_BASE_PATH, "patch/main"), "fuse-patch-core", false);
        writeDummyModule(new File(MODULE_BASE_PATH, "config/main"), "fuse-patch-config", false);

        File patchModule = new File(MODULE_BASE_PATH, "patch/main");
        File configModule = new File(MODULE_BASE_PATH, "config/main");

        assertExpectedFileCount(patchModule, 3);
        assertExpectedFileCount(configModule, 3);

        server.cleanUp();

        assertExpectedFileCount(patchModule, 3);
        assertExpectedFileCount(configModule, 3);
    }

    private void assertExpectedFileCount(File modulePath, int count) {
        FilenameFilter filter = new ModuleFileFilter();
        String[] moduleFiles = modulePath.list(filter);

        Assert.assertEquals("Expected " + count + " files to be present after clean up", count, moduleFiles.length);
    }

    private void writeDummyModule(File modulePath, String name, boolean addOldVersion) throws Exception {
        modulePath.mkdirs();

        FileWriter fw = new FileWriter(new File(modulePath, "module.xml"));
        try {
            fw.write("<module name=\"" + name + "\">\n");
            fw.write("<resources>\n");
            fw.write("<resource-root path=\"" + name + "-1.0.1.jar\"/>\n");
            fw.write("<resource-root path=\"" + name + "-test-1.0.1.jar\"/>\n");
            fw.write("</resources>\n");
            fw.write("</module>");
        } finally {
            fw.close();
        }

        new File(modulePath, name + "-1.0.1.jar").createNewFile();
        new File(modulePath, name + "-test-1.0.1.jar").createNewFile();

        if (addOldVersion) {
            new File(modulePath, name + "-1.0.0.jar").createNewFile();
            new File(modulePath, name + "-test-1.0.0.jar").createNewFile();
        }
    }

    private class ModuleFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar") || name.endsWith(".xml");
        }
    }
}
