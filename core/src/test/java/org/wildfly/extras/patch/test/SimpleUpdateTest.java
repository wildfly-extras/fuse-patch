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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.repository.ParserAccess;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleUpdateTest {

    final static File repoPath = new File("target/repos/SimpleUpdateTest/repo");
    final static File[] serverPaths = new File[5];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.mkdirs();
        for (int i = 0; i < 5; i++) {
            serverPaths[i] = new File("target/servers/SimpleUpdateTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].mkdirs();
        }
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        patchTool.getRepository().addArchive(Archives.getZipUrlFoo100());
        patchTool.getRepository().addArchive(Archives.getZipUrlFoo110());
    }

    @Test
    public void testInstallUpdateUninstall() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[0]).build();
        Server server = patchTool.getServer();
        
        List<PatchId> patches = server.queryAppliedPatches();
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
        Patch setA = ParserAccess.getPatch(Archives.getZipUrlFoo100());
        PatchId idA = setA.getPatchId();
        SmartPatch smartPatch = SmartPatch.forInstall(setA, new DataHandler(new URLDataSource(Archives.getZipUrlFoo100())));
        Assert.assertEquals(idA, smartPatch.getPatchId());
        Assert.assertEquals(setA.getRecords(), smartPatch.getRecords());
        
        // Install foo-1.0.0
        Patch curSet = patchTool.install(idA, false);
        Assert.assertEquals(idA, curSet.getPatchId());
        Assert.assertEquals(setA.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setA.getRecords(), server.getPatch(idA).getRecords());
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
        Patch setB = ParserAccess.getPatch(Archives.getZipUrlFoo110());
        PatchId idB = setB.getPatchId();
        Patch smartSet = Patch.smartDelta(setA, setB);
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
        Archives.assertPathsEqual(setB.getRecords(), server.getPatch(idB).getRecords());
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
        patches = server.queryAppliedPatches();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(idB, patches.get(0));
        Assert.assertEquals(setB, server.getPatch("foo"));
        
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
        smartSet = Patch.smartDelta(setB, setA);
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
        Archives.assertPathsEqual(setA.getRecords(), server.getPatch(idA).getRecords());
        Archives.assertPathsEqual(setA, serverPaths[0]);
        
        // Query applied packages
        patches = server.queryAppliedPatches();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(idA, patches.get(0));
        Assert.assertEquals(setA, server.getPatch("foo"));
        
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
        patches = server.queryAppliedPatches();
        Assert.assertEquals(0, patches.size());
    }
    
    @Test
    public void testAddingFileThatExists() throws Exception {
        
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[1]).build();
        Server server = patchTool.getServer();
        
        File targetPath = new File(serverPaths[1], "config/propsA.properties");
        
        // Copy a file to the server
        targetPath.getParentFile().mkdirs();
        FileChannel input = new FileInputStream(new File("src/test/resources/propsA2.properties")).getChannel();
        try {
            FileChannel output = new FileOutputStream(targetPath).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
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
        IOUtils.rmdirs(new File(serverPaths[1], "fusepatch"));
        Assert.assertTrue("No patches applied", server.queryAppliedPatches().isEmpty());
        
        // Verify that the files can be added if they have the same checksum
        patchTool.install(idA, false);
        Assert.assertEquals(1, server.queryAppliedPatches().size());
    }

    @Test
    public void testOverrideModifiedFile() throws Exception {
        
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[2]).build();
        
        File targetPath = new File(serverPaths[2], "config/propsA.properties");
        
        // Install foo-1.0.0
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        patchTool.install(idA, false);
        assertFileContent("some.prop = A1", targetPath);
        FileChannel input = new FileInputStream(new File("src/test/resources/propsA2.properties")).getChannel();
        try {
            FileChannel output = new FileOutputStream(targetPath).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        
        
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
        
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[3]).build();
        
        File targetPath = new File(serverPaths[3], "config/remove-me.properties");
        
        // Install foo-1.0.0
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        patchTool.install(idA, false);
        assertFileContent("some.prop = A1", targetPath);
        
        targetPath.delete();
        
        // Install foo-1.1.0
        PatchId idB = PatchId.fromURL(Archives.getZipUrlFoo110());
        patchTool.install(idB, false);
    }

    private void assertFileContent(String exp, File path) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(path));
        try {
            String line = br.readLine();
            Assert.assertEquals(exp, line);
        } finally {
            br.close();
        }
    }
}
