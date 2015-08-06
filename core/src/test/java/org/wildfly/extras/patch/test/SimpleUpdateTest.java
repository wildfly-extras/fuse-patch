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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.ParserAccess;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleUpdateTest {

    final static Path[] serverPaths = new Path[5];

    @BeforeClass
    public static void setUp() throws Exception {
        for (int i = 0; i < 5; i++) {
            serverPaths[i] = Paths.get("target/servers/SimpleUpdateTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].toFile().mkdirs();
        }
    }

    @Test
    public void testInstallUpdateUninstall() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPaths[0]).build();
        Server server = patchTool.getServer();
        
        List<PatchId> patches = server.queryAppliedPackages();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Verify smart patch A
        Package setA = ParserAccess.getPackage(Archives.getZipFileFoo100());
        PatchId patchId = setA.getPatchId();
        SmartPatch smartPatch = new SmartPatch(setA, Archives.getZipFileFoo100());
        Assert.assertEquals(patchId, smartPatch.getPatchId());
        Assert.assertEquals(setA.getRecords(), smartPatch.getRecords());
        
        // Apply smart patch A
        Package curSet = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(patchId, curSet.getPatchId());
        Assert.assertEquals(setA.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setA.getRecords(), server.getPackage(patchId).getRecords());
        Archives.assertPathsEqual(setA, serverPaths[0]);

        // Verify managed paths
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(4, mpaths.size());
        Assert.assertEquals("config/propsA.properties [foo-1.0.0]", mpaths.get(0).toString());
        Assert.assertEquals("config/propsB.properties [foo-1.0.0]", mpaths.get(1).toString());
        Assert.assertEquals("config/remove-me.properties [foo-1.0.0]", mpaths.get(2).toString());
        Assert.assertEquals("lib/foo-1.0.0.jar [foo-1.0.0]", mpaths.get(3).toString());
        
        // Verify smart patch B
        Package setB = ParserAccess.getPackage(Archives.getZipFileFoo110());
        patchId = setB.getPatchId();
        Package smartSet = Package.smartSet(setA, setB);
        smartPatch = new SmartPatch(smartSet, Archives.getZipFileFoo110());
        Assert.assertEquals(4, smartPatch.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartPatch.getRecords().get(0));
        Archives.assertActionPathEquals("DEL config/remove-me.properties", smartPatch.getRecords().get(1));
        Archives.assertActionPathEquals("DEL lib/foo-1.0.0.jar", smartPatch.getRecords().get(2));
        Archives.assertActionPathEquals("ADD lib/foo-1.1.0.jar", smartPatch.getRecords().get(3));

        // Apply smart patch B
        curSet = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(patchId, curSet.getPatchId());
        Assert.assertEquals(3, curSet.getRecords().size());
        Assert.assertEquals(setB.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setB.getRecords(), server.getPackage(patchId).getRecords());
        Archives.assertPathsEqual(setB, serverPaths[0]);
        
        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(3, mpaths.size());
        Assert.assertEquals("config/propsA.properties [foo-1.1.0]", mpaths.get(0).toString());
        Assert.assertEquals("config/propsB.properties [foo-1.0.0]", mpaths.get(1).toString());
        Assert.assertEquals("lib/foo-1.1.0.jar [foo-1.1.0]", mpaths.get(2).toString());
        
        // Verify selected managed paths
        mpaths = server.queryManagedPaths("config/props");
        Assert.assertEquals(2, mpaths.size());
        Assert.assertEquals("config/propsA.properties [foo-1.1.0]", mpaths.get(0).toString());
        Assert.assertEquals("config/propsB.properties [foo-1.0.0]", mpaths.get(1).toString());
        
        // Verify that we can query the set
        patches = server.queryAppliedPackages();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(patchId, patches.get(0));
        Assert.assertEquals(curSet, server.getPackage("foo"));
        
        // Cannot uninstall non-existing package
        try {
            patchTool.uninstall(PatchId.fromString("xxx-1.0.0"), false);
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("not installed: xxx-1.0.0"));
        }
        
        // Cannot uninstall old package
        try {
            patchTool.uninstall(setA.getPatchId(), false);
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("" + setB.getPatchId()));
            Assert.assertTrue(message, message.contains("cannot uninstall: " + setA.getPatchId()));
        }
        
        // Uninstall the package
        curSet = patchTool.uninstall(patchId, false);
        Assert.assertEquals(patchId, curSet.getPatchId());
        Assert.assertEquals(3, curSet.getRecords().size());
        Archives.assertActionPathEquals("DEL config/propsA.properties", curSet.getRecords().get(0));
        Archives.assertActionPathEquals("DEL config/propsB.properties", curSet.getRecords().get(1));
        Archives.assertActionPathEquals("DEL lib/foo-1.1.0.jar", curSet.getRecords().get(2));

        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(0, mpaths.size());

        // Verify query
        patches = server.queryAppliedPackages();
        Assert.assertEquals(0, patches.size());
    }
    
    @Test
    public void testAddingFileThatExists() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPaths[1]).build();
        Server server = patchTool.getServer();
        
        Path targetPath = serverPaths[1].resolve("config/propsA.properties");
        
        // Copy a file to the server
        targetPath.getParent().toFile().mkdirs();
        Files.copy(Paths.get("src/test/resources/propsA2.properties"), targetPath);
        assertFileContent("some.prop = A2", targetPath);
        
        Package setA = ParserAccess.getPackage(Archives.getZipFileFoo100());
        SmartPatch smartPatch = new SmartPatch(setA, Archives.getZipFileFoo100());
        try {
            server.applySmartPatch(smartPatch, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            Assert.assertTrue(ex.getMessage().contains("existing file config" + File.separator  + "propsA.properties"));
        }
        
        // Force the the override
        server.applySmartPatch(smartPatch, true);
        assertFileContent("some.prop = A1", targetPath);
        
        // Delete the workspace
        IOUtils.rmdirs(serverPaths[1].resolve("fusepatch"));
        Assert.assertTrue("No patches applied", server.queryAppliedPackages().isEmpty());
        
        // Verify that the files can be added if they have the same checksum
        server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(1, server.queryAppliedPackages().size());
    }

    @Test
    public void testOverrideModifiedFile() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPaths[2]).build();
        Server server = patchTool.getServer();
        
        Path targetPath = serverPaths[2].resolve("config/propsA.properties");
        
        // Install foo-1.0.0
        Package setA = ParserAccess.getPackage(Archives.getZipFileFoo100());
        SmartPatch smartPatch = new SmartPatch(setA, Archives.getZipFileFoo100());
        Package seedPatch = server.applySmartPatch(smartPatch, false);
        assertFileContent("some.prop = A1", targetPath);
        
        Files.copy(Paths.get("src/test/resources/propsA2.properties"), targetPath, REPLACE_EXISTING);
        
        // Install foo-1.1.0
        Package setB = ParserAccess.getPackage(Archives.getZipFileFoo110());
        smartPatch = new SmartPatch(Package.smartSet(seedPatch, setB), Archives.getZipFileFoo110());
        try {
            server.applySmartPatch(smartPatch, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            Assert.assertTrue(ex.getMessage().contains("already modified file config" + File.separator + "propsA.properties"));
        }
        
        // force the the override
        server.applySmartPatch(smartPatch, true);
        assertFileContent("some.prop = A2", targetPath);
    }

    @Test
    public void testRemoveNonExistingFile() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPaths[3]).build();
        Server server = patchTool.getServer();
        
        Path targetPath = serverPaths[3].resolve("config/remove-me.properties");
        
        // Install foo-1.0.0
        Package setA = ParserAccess.getPackage(Archives.getZipFileFoo100());
        SmartPatch smartPatch = new SmartPatch(setA, Archives.getZipFileFoo100());
        Package seedPatch = server.applySmartPatch(smartPatch, false);
        assertFileContent("some.prop = A1", targetPath);
        
        targetPath.toFile().delete();
        
        // Install foo-1.1.0
        Package setB = ParserAccess.getPackage(Archives.getZipFileFoo110());
        smartPatch = new SmartPatch(Package.smartSet(seedPatch, setB), Archives.getZipFileFoo110());
        server.applySmartPatch(smartPatch, false);
    }

    private void assertFileContent(String exp, Path path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line = br.readLine();
            Assert.assertEquals(exp, line);
        }
    }
}
