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
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * [#100] Directories not deleted during update
 * 
 * https://github.com/wildfly-extras/fuse-patch/issues/100
 */
public class RemoveDirOnUpdateTest {

    final static Path repoPath = Paths.get("target/repos/RemoveDirOnUpdateTest/repo");
    final static Path[] serverPaths = new Path[2];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.toFile().mkdirs();
        for (int i = 0; i < 2; i++) {
            serverPaths[i] = Paths.get("target/servers/RemoveDirOnUpdateTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].toFile().mkdirs();
        }
        URL repoURL = repoPath.toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        patchTool.getRepository().addArchive(getZipUrlRdou100());
        patchTool.getRepository().addArchive(getZipUrlRdou110());
    }

    @Test
    public void testRemoveDirOnUpdate() throws Exception {

        URL repoURL = repoPath.toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[0]).build();
        Server server = patchTool.getServer();
        
        Path configPath = serverPaths[0].resolve("config");
        Path subPath = configPath.resolve("sub");
        
        Patch curSet = patchTool.install(PatchId.fromString("rdou-1.0.0"), false);
        Assert.assertEquals(2, curSet.getRecords().size());
        
        // Verify managed paths for rdou-1.0.0
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(5, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config [rdou-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/sub [rdou-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("config/sub/propsA.properties [rdou-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("lib [rdou-1.0.0]"), mpaths.get(3));
        Assert.assertEquals(ManagedPath.fromString("lib/rdou-1.0.0.jar [rdou-1.0.0]"), mpaths.get(4));

        // Verify that the config dir exists
        Assert.assertTrue(configPath.toFile().isDirectory());
        Assert.assertTrue(subPath.toFile().isDirectory());
        
        curSet = patchTool.update("rdou", false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths for rdou-1.0.0
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(2, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("lib [rdou-1.1.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("lib/rdou-1.1.0.jar [rdou-1.1.0]"), mpaths.get(1));

        // Verify that the config dir was removed
        Assert.assertFalse(subPath.toFile().exists());
        Assert.assertFalse(configPath.toFile().exists());
    }

    @Test
    public void testKeepDirOnUpdate() throws Exception {

        URL repoURL = repoPath.toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[1]).build();
        Server server = patchTool.getServer();
        
        Path configPath = serverPaths[1].resolve("config");
        Path subPath = configPath.resolve("sub");
        
        // Create the config path upfront
        configPath.toFile().mkdirs();
        
        Patch curSet = patchTool.install(PatchId.fromString("rdou-1.0.0"), false);
        Assert.assertEquals(2, curSet.getRecords().size());
        
        // Verify managed paths for rdou-1.0.0
        List<ManagedPath> mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(4, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("config/sub [rdou-1.0.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("config/sub/propsA.properties [rdou-1.0.0]"), mpaths.get(1));
        Assert.assertEquals(ManagedPath.fromString("lib [rdou-1.0.0]"), mpaths.get(2));
        Assert.assertEquals(ManagedPath.fromString("lib/rdou-1.0.0.jar [rdou-1.0.0]"), mpaths.get(3));

        // Verify that the config dir exists
        Assert.assertTrue(configPath.toFile().isDirectory());
        Assert.assertTrue(subPath.toFile().isDirectory());
        
        curSet = patchTool.update("rdou", false);
        Assert.assertEquals(1, curSet.getRecords().size());
        
        // Verify managed paths for rdou-1.0.0
        mpaths = server.queryManagedPaths(null);
        Assert.assertEquals(2, mpaths.size());
        Assert.assertEquals(ManagedPath.fromString("lib [rdou-1.1.0]"), mpaths.get(0));
        Assert.assertEquals(ManagedPath.fromString("lib/rdou-1.1.0.jar [rdou-1.1.0]"), mpaths.get(1));

        // Verify that the config dir was removed
        Assert.assertFalse(subPath.toFile().exists());
        Assert.assertTrue(configPath.toFile().exists());
    }

    /**
     * rdou-1.0.0.zip
     * 
     * config/sub/propsA.properties
     * lib/rdou-1.0.0.jar
     */
    static URL getZipUrlRdou100() throws IOException {
        File targetFile = Paths.get("target/rdou-1.0.0.zip").toFile();
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "rdou-1.0.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/sub/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }

    /**
     * rdou-1.1.0.zip
     * 
     * lib/rdou-1.1.0.jar
     */
    static URL getZipUrlRdou110() throws IOException {
        File targetFile = Paths.get("target/rdou-1.1.0.zip").toFile();
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "rdou-1.1.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }
}
