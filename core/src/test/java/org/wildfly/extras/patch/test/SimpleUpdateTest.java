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

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.repository.ParserAccess;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleUpdateTest {

    final static Path repoPath = Paths.get("target/repos/SimpleUpdateTest/repo");
    final static Path[] serverPaths = new Path[5];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.toFile().mkdirs();
        for (int i = 0; i < 5; i++) {
            serverPaths[i] = Paths.get("target/servers/SimpleUpdateTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].toFile().mkdirs();
        }
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).build();
        patchTool.getRepository().addArchive(Archives.getZipUrlFoo100());
        patchTool.getRepository().addArchive(Archives.getZipUrlFoo110());
    }

    @Test
    public void testInstallUpdateUninstall() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).serverPath(serverPaths[0]).build();
        Server server = patchTool.getServer();
        
        List<PatchId> patches = server.queryAppliedPackages();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Cannot install non-existing package
        try {
            patchTool.install(PatchId.fromString("xxx-1.0.0"), false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("does not contain package: xxx-1.0.0"));
        }
        
        // Verify smart patch to install foo-1.0.0
        Package setA = ParserAccess.getPackage(Archives.getZipUrlFoo100());
        PatchId idA = setA.getPatchId();
        SmartPatch smartPatch = SmartPatch.forInstall(setA, new DataHandler(new URLDataSource(Archives.getZipUrlFoo100())));
        Assert.assertEquals(idA, smartPatch.getPatchId());
        Assert.assertEquals(setA.getRecords(), smartPatch.getRecords());
        
        // Install foo-1.0.0
        Package curSet = patchTool.install(idA, false);
        Assert.assertEquals(idA, curSet.getPatchId());
        Assert.assertEquals(setA.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setA.getRecords(), server.getPackage(idA).getRecords());
        Archives.assertPathsEqual(setA, serverPaths[0]);

        // Verify managed paths for foo-1.0.0
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(6, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [foo-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [foo-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("config/propsB.properties [foo-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("config/remove-me.properties [foo-1.0.0]"), mpaths.get(3));
        Assert.assertEquals(ManagedPath.fromString("lib [foo-1.0.0]"), mpaths.get(4));
        Assert.assertEquals(ManagedPath.fromString("lib/foo-1.0.0.jar [foo-1.0.0]"), mpaths.get(5));
        
        // Verify smart patch to update to foo-1.1.0
        Package setB = ParserAccess.getPackage(Archives.getZipUrlFoo110());
        PatchId idB = setB.getPatchId();
        Package smartSet = Package.smartDelta(setA, setB);
        smartPatch = SmartPatch.forInstall(smartSet, new DataHandler(new URLDataSource(Archives.getZipUrlFoo110())));
        Assert.assertEquals(4, smartPatch.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartPatch.getRecords().get(0));
        Archives.assertActionPathEquals("DEL config/remove-me.properties", smartPatch.getRecords().get(1));
        Archives.assertActionPathEquals("DEL lib/foo-1.0.0.jar", smartPatch.getRecords().get(2));
        Archives.assertActionPathEquals("ADD lib/foo-1.1.0.jar", smartPatch.getRecords().get(3));

        // Update to foo-1.1.0
        curSet = patchTool.update("foo", false);
        Assert.assertEquals(idB, curSet.getPatchId());
        Assert.assertEquals(3, curSet.getRecords().size());
        Assert.assertEquals(setB.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setB.getRecords(), server.getPackage(idB).getRecords());
        Archives.assertPathsEqual(setB, serverPaths[0]);
        
        // Verify managed paths for foo-1.1.0
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(5, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [foo-1.1.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [foo-1.1.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("config/propsB.properties [foo-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("lib [foo-1.1.0]"), mpaths.get(3));
        Assert.assertEquals(ManagedPath.fromString("lib/foo-1.1.0.jar [foo-1.1.0]"), mpaths.get(4));
        
        // Verify selected managed paths for foo-1.1.0
        mpaths = server.queryManagedPaths("config" + File.separator + "props");
        Assert.assertEquals(2, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [foo-1.1.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsB.properties [foo-1.0.0]"), mpaths.get(1));
        
        // Query applied packages
        patches = server.queryAppliedPackages();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(idB, patches.get(0));
        Assert.assertEquals(setB, server.getPackage("foo"));
        
        // Cannot uninstall non-existing package
        try {
            patchTool.uninstall(PatchId.fromString("xxx-1.0.0"));
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("not installed: xxx-1.0.0"));
        }
        
        // Cannot uninstall old package
        try {
            patchTool.uninstall(setA.getPatchId());
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("" + setB.getPatchId()));
            Assert.assertTrue(message, message.contains("cannot uninstall: " + setA.getPatchId()));
        }
        
        // Verify smart patch to downgrade to foo-1.0.0
        smartSet = Package.smartDelta(setB, setA);
        smartPatch = SmartPatch.forInstall(smartSet, new DataHandler(new URLDataSource(Archives.getZipUrlFoo100())));
        Assert.assertEquals(4, smartPatch.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartPatch.getRecords().get(0));
        Archives.assertActionPathEquals("ADD config/remove-me.properties", smartPatch.getRecords().get(1));
        Archives.assertActionPathEquals("ADD lib/foo-1.0.0.jar", smartPatch.getRecords().get(2));
        Archives.assertActionPathEquals("DEL lib/foo-1.1.0.jar", smartPatch.getRecords().get(3));
        
        // Downgrade to foo-1.0.0
        curSet = patchTool.install(idA, false);
        Assert.assertEquals(idA, curSet.getPatchId());
        Assert.assertEquals(4, curSet.getRecords().size());
        Assert.assertEquals(setA.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setA.getRecords(), server.getPackage(idA).getRecords());
        Archives.assertPathsEqual(setA, serverPaths[0]);
        
        // Query applied packages
        patches = server.queryAppliedPackages();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(idA, patches.get(0));
        Assert.assertEquals(setA, server.getPackage("foo"));
        
        // Verify managed paths for foo-1.0.0
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(6, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [foo-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [foo-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("config/propsB.properties [foo-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("config/remove-me.properties [foo-1.0.0]"), mpaths.get(3));
        Assert.assertEquals(ManagedPath.fromString("lib [foo-1.0.0]"), mpaths.get(4));
        Assert.assertEquals(ManagedPath.fromString("lib/foo-1.0.0.jar [foo-1.0.0]"), mpaths.get(5));
        
        // Uninstall the package
        curSet = patchTool.uninstall(idA);
        Assert.assertEquals(idA, curSet.getPatchId());
        Assert.assertEquals(4, curSet.getRecords().size());
        Archives.assertActionPathEquals("DEL config/propsA.properties", curSet.getRecords().get(0));
        Archives.assertActionPathEquals("DEL config/propsB.properties", curSet.getRecords().get(1));
        Archives.assertActionPathEquals("DEL config/remove-me.properties", curSet.getRecords().get(2));
        Archives.assertActionPathEquals("DEL lib/foo-1.0.0.jar", curSet.getRecords().get(3));

        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(0, mpaths.size());

        // Verify query
        patches = server.queryAppliedPackages();
        Assert.assertEquals(0, patches.size());
    }
    
    @Test
    public void testAddingFileThatExists() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).serverPath(serverPaths[1]).build();
        Server server = patchTool.getServer();
        
        Path targetPath = serverPaths[1].resolve("config/propsA.properties");
        
        // Copy a file to the server
        targetPath.getParent().toFile().mkdirs();
        Files.copy(Paths.get("src/test/resources/propsA2.properties"), targetPath);
        assertFileContent("some.prop = A2", targetPath);
        
        // Install foo-1.0.0
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        try {
            patchTool.install(idA, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            Assert.assertTrue(ex.getMessage().contains("existing file config" + File.separator  + "propsA.properties"));
        }
        
        // Force the the override
        patchTool.install(idA, true);
        assertFileContent("some.prop = A1", targetPath);
        
        // Delete the workspace
        IOUtils.rmdirs(serverPaths[1].resolve("fusepatch"));
        Assert.assertTrue("No patches applied", server.queryAppliedPackages().isEmpty());
        
        // Verify that the files can be added if they have the same checksum
        patchTool.install(idA, false);
        Assert.assertEquals(1, server.queryAppliedPackages().size());
    }

    @Test
    public void testOverrideModifiedFile() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).serverPath(serverPaths[2]).build();
        
        Path targetPath = serverPaths[2].resolve("config/propsA.properties");
        
        // Install foo-1.0.0
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        patchTool.install(idA, false);
        assertFileContent("some.prop = A1", targetPath);
        
        Files.copy(Paths.get("src/test/resources/propsA2.properties"), targetPath, REPLACE_EXISTING);
        
        // Install foo-1.1.0
        PatchId idB = PatchId.fromURL(Archives.getZipUrlFoo110());
        try {
            patchTool.install(idB, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            Assert.assertTrue(ex.getMessage().contains("already modified file config" + File.separator + "propsA.properties"));
        }
        
        // force the the override
        patchTool.install(idB, true);
        assertFileContent("some.prop = A2", targetPath);
    }

    @Test
    public void testRemoveNonExistingFile() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).serverPath(serverPaths[3]).build();
        
        Path targetPath = serverPaths[3].resolve("config/remove-me.properties");
        
        // Install foo-1.0.0
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        patchTool.install(idA, false);
        assertFileContent("some.prop = A1", targetPath);
        
        targetPath.toFile().delete();
        
        // Install foo-1.1.0
        PatchId idB = PatchId.fromURL(Archives.getZipUrlFoo110());
        patchTool.install(idB, false);
    }

    private void assertFileContent(String exp, Path path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line = br.readLine();
            Assert.assertEquals(exp, line);
        }
    }
}
