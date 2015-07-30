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
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.ServerInstance;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.ParserAccess;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleUpdateTest {

    final static Path serverPathA = Paths.get("target/servers/SimpleUpdateTest/srvA");
    final static Path serverPathB = Paths.get("target/servers/SimpleUpdateTest/srvB");
    final static Path serverPathC = Paths.get("target/servers/SimpleUpdateTest/srvC");
    final static Path serverPathD = Paths.get("target/servers/SimpleUpdateTest/srvD");
    final static Path serverPathE = Paths.get("target/servers/SimpleUpdateTest/srvE");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPathA);
        IOUtils.rmdirs(serverPathB);
        IOUtils.rmdirs(serverPathC);
        IOUtils.rmdirs(serverPathD);
        IOUtils.rmdirs(serverPathE);
        serverPathA.toFile().mkdirs();
        serverPathB.toFile().mkdirs();
        serverPathC.toFile().mkdirs();
        serverPathD.toFile().mkdirs();
        serverPathE.toFile().mkdirs();
    }

    @Test
    public void testSimpleUpdate() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPathA).build();
        ServerInstance server = patchTool.getServerInstance();
        
        List<PatchId> patches = server.queryAppliedPatches();
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
        Archives.assertPathsEqual(setA, serverPathA);

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
        Archives.assertPathsEqual(setB, serverPathA);
        
        // verify that we can query the set
        patches = server.queryAppliedPatches();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(patchId, patches.get(0));
        Assert.assertEquals(curSet, server.getPackage("foo"));
    }
    
    @Test
    public void testAddingFileThatExists() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPathB).build();
        ServerInstance server = patchTool.getServerInstance();
        
        Path targetPath = serverPathB.resolve("config/propsA.properties");
        
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
        IOUtils.rmdirs(serverPathB.resolve("fusepatch"));
        Assert.assertTrue("No patches applied", server.queryAppliedPatches().isEmpty());
        
        // Verify that the files can be added if they have the same checksum
        server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(1, server.queryAppliedPatches().size());
    }

    @Test
    public void testOverrideModifiedFile() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPathC).build();
        ServerInstance server = patchTool.getServerInstance();
        
        Path targetPath = serverPathC.resolve("config/propsA.properties");
        
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
        
        PatchTool patchTool = new PatchToolBuilder().serverPath(serverPathD).build();
        ServerInstance server = patchTool.getServerInstance();
        
        Path targetPath = serverPathD.resolve("config/remove-me.properties");
        
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
