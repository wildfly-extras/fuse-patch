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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;

public class WildFlyServerCleanUpTest {

    private static Path SERVER_PATH = Paths.get("target/my-server");
    private static Path MODULE_BASE_PATH = Paths.get("target/my-server/modules/system/layers/fuse/org/wildfly/extras");

    @Before
    public void setUp() throws Exception {
        SERVER_PATH.resolve("fusepatch/repository").toFile().mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        Files.walkFileTree(SERVER_PATH, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception == null) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void testServerCleanUp() throws Exception {
        PatchToolBuilder builder = new PatchToolBuilder();
        PatchTool patchTool = builder.serverPath(SERVER_PATH).build();
        Server server = patchTool.getServer();

        writeDummyModule(MODULE_BASE_PATH.resolve("patch/main"), "fuse-patch-core", true);
        writeDummyModule(MODULE_BASE_PATH.resolve("config/main"), "fuse-patch-config", true);

        Path patchModule = MODULE_BASE_PATH.resolve("patch/main");
        Path configModule = MODULE_BASE_PATH.resolve("config/main");

        // Assert files present before cleanup
        assertExpectedFileCount(patchModule, 5);
        assertExpectedFileCount(configModule, 5);

        server.cleanUp();

        // Make sure the correct jars and the module.xml remain
        assertExpectedFileCount(patchModule, 3);
        assertExpectedFileCount(configModule, 3);

        Assert.assertTrue(patchModule.resolve("fuse-patch-core-1.0.1.jar").toFile().exists());
        Assert.assertTrue(patchModule.resolve("fuse-patch-core-test-1.0.1.jar").toFile().exists());
        Assert.assertTrue(patchModule.resolve("module.xml").toFile().exists());

        Assert.assertTrue(configModule.resolve("fuse-patch-config-1.0.1.jar").toFile().exists());
        Assert.assertTrue(configModule.resolve("fuse-patch-config-test-1.0.1.jar").toFile().exists());
        Assert.assertTrue(configModule.resolve("module.xml").toFile().exists());
    }

    @Test
    public void testServerCleanUpNoOp() throws Exception {
        PatchToolBuilder builder = new PatchToolBuilder();
        PatchTool patchTool = builder.serverPath(SERVER_PATH).build();
        Server server = patchTool.getServer();

        writeDummyModule(MODULE_BASE_PATH.resolve("patch/main"), "fuse-patch-core", false);
        writeDummyModule(MODULE_BASE_PATH.resolve("config/main"), "fuse-patch-config", false);

        Path patchModule = MODULE_BASE_PATH.resolve("patch/main");
        Path configModule = MODULE_BASE_PATH.resolve("config/main");

        assertExpectedFileCount(patchModule, 3);
        assertExpectedFileCount(configModule, 3);

        server.cleanUp();

        assertExpectedFileCount(patchModule, 3);
        assertExpectedFileCount(configModule, 3);
    }

    private void assertExpectedFileCount(Path modulePath, int count) {
        FilenameFilter filter = new ModuleFileFilter();
        String[] moduleFiles = modulePath.toFile().list(filter);

        Assert.assertEquals("Expected " + count + " files to be present after clean up", count, moduleFiles.length);
    }

    private void writeDummyModule(Path modulePath, String name, boolean addOldVersion) throws Exception {
        modulePath.toFile().mkdirs();

        try (FileWriter fw = new FileWriter(modulePath.resolve("module.xml").toFile())) {
            fw.write("<module name=\"" + name + "\">\n");
            fw.write("<resources>\n");
            fw.write("<resource-root path=\"" + name + "-1.0.1.jar\"/>\n");
            fw.write("<resource-root path=\"" + name + "-test-1.0.1.jar\"/>\n");
            fw.write("</resources>\n");
            fw.write("</module>");
        }

        modulePath.resolve(name + "-1.0.1.jar").toFile().createNewFile();
        modulePath.resolve(name + "-test-1.0.1.jar").toFile().createNewFile();

        if (addOldVersion) {
            modulePath.resolve(name + "-1.0.0.jar").toFile().createNewFile();
            modulePath.resolve(name + "-test-1.0.0.jar").toFile().createNewFile();
        }
    }

    private class ModuleFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar") || name.endsWith(".xml");
        }
    }
}
