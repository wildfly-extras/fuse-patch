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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.List;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.test.subA.ClassA;
import org.wildfly.extras.patch.utils.IOUtils;

/**
 * [#57] Already existing paths may incorrectly get removed on update
 * 
 * https://github.com/wildfly-extras/fuse-patch/issues/57
 */
public class OverrideExistingPathTest {

    final static File repoPath = new File("target/repos/OverrideExistingPathTest/repo");
    final static File[] serverPaths = new File[2];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.mkdirs();
        for (int i = 0; i < 2; i++) {
            serverPaths[i] = new File("target/servers/OverrideExistingPathTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].mkdirs();
        }
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        patchTool.getRepository().addArchive(getZipUrlFoo100());
        patchTool.getRepository().addArchive(getZipUrlBar100(), true);
        patchTool.getRepository().addArchive(getZipUrlBar110(), true);
    }

    @Test
    public void testPathExistsInServer() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[0]).build();
        Server server = patchTool.getServer();
        
        // Add a file to the server upfront
        File filePath = new File(serverPaths[0], "config" + File.separator + "propsA.properties");
        filePath.getParentFile().mkdirs();
        FileChannel input = new FileInputStream(new File("src/test/resources/propsA1.properties")).getChannel();
        try {
            FileChannel output = new FileOutputStream(filePath).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        Assert.assertTrue(filePath.isFile());
        
        // Install oepbar-1.0.0
        Patch curSet = patchTool.install(PatchId.fromString("oepbar-1.0.0"), false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(1, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [server-0.0.0, oepbar-1.0.0]"), mpaths.get(0));
        
        // Uninstall oepbar-1.0.0
        curSet = patchTool.uninstall(PatchId.fromString("oepbar-1.0.0"));
        Assert.assertEquals(1, curSet.getRecords().size());

        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(0, mpaths.size());

        // Assert that the file is still there
        Assert.assertTrue(filePath.isFile());
        
        // Install oepbar-1.0.0
        curSet = patchTool.install(PatchId.fromString("oepbar-1.0.0"), false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Update oepbar-1.1.0
        curSet = patchTool.update("oepbar", false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(1, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [server-0.0.0, oepbar-1.1.0]"), mpaths.get(0));
        
        // Uninstall oepbar-1.1.0
        curSet = patchTool.uninstall(PatchId.fromString("oepbar-1.1.0"));
        Assert.assertEquals(1, curSet.getRecords().size());

        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(0, mpaths.size());

        // Assert that the file is still there
        Assert.assertTrue(filePath.isFile());
    }

    @Test
    public void testPathExistsInFoo() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[1]).build();
        Server server = patchTool.getServer();
        
        // Install oepfoo-1.0.0
        Patch curSet = patchTool.install(PatchId.fromString("oepfoo-1.0.0"), false);
        Assert.assertEquals(2, curSet.getRecords().size());
        
        // Verify managed paths
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(4, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [oepfoo-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [oepfoo-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("lib [oepfoo-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("lib/oep-1.0.0.jar [oepfoo-1.0.0]"), mpaths.get(3));
        
        // Install oepbar-1.0.0
        curSet = patchTool.install(PatchId.fromString("oepbar-1.0.0"), false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(4, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [oepfoo-1.0.0, oepbar-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [oepfoo-1.0.0, oepbar-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("lib [oepfoo-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("lib/oep-1.0.0.jar [oepfoo-1.0.0]"), mpaths.get(3));

        // Uninstall oepfoo-1.0.0
        curSet = patchTool.uninstall(PatchId.fromString("oepfoo-1.0.0"));
        Assert.assertEquals(2, curSet.getRecords().size());
        
        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(2, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [oepbar-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/propsA.properties [oepbar-1.0.0]"), mpaths.get(1));

        // Uninstall oepbar-1.0.0
        curSet = patchTool.uninstall(PatchId.fromString("oepbar-1.0.0"));
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(0, mpaths.size());
    }
    
    /**
     * oepfoo-1.0.0.zip
     * 
     * config/propsA.properties
     * lib/oep-1.0.0.jar
     */
    static URL getZipUrlFoo100() throws IOException {
        File targetFile = new File("target/oepfoo-1.0.0.zip");
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "oep-1.0.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }

    /**
     * oepbar-1.0.0.zip
     * 
     * config/propsA.properties
     */
    static URL getZipUrlBar100() throws IOException {
        File targetFile = new File("target/oepbar-1.0.0.zip");
        if (!targetFile.exists()) {
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }

    /**
     * oepbar-1.1.0.zip
     * 
     * config/propsA.properties
     */
    static URL getZipUrlBar110() throws IOException {
        File targetFile = new File("target/oepbar-1.1.0.zip");
        if (!targetFile.exists()) {
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }
}
