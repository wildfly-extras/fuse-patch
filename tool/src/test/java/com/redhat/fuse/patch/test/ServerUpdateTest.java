/*
 * #%L
 * Fuse Patch :: Parser
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
package com.redhat.fuse.patch.test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.fuse.patch.ArtefactId;
import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchPool;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.SmartPatch;
import com.redhat.fuse.patch.internal.DefaultPatchPool;
import com.redhat.fuse.patch.internal.WildFlyServerInstance;
import com.redhat.fuse.patch.utils.IOUtils;

public class ServerUpdateTest {

    final static Path targetPath = Paths.get("target/servers/serverB");
    final static Path poolPath = Paths.get("target/pools/poolB");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(targetPath);
        IOUtils.copydirs(targetPath, Paths.get("src/test/etc/wildfly"));
        QueryPoolTest.setupPoolA(poolPath);
        QueryPoolTest.setupPoolB(poolPath);
    }

    @Test
    public void testServerUpdate() throws Exception {

        WildFlyServerInstance server = new WildFlyServerInstance(targetPath);
        PatchPool pool = new DefaultPatchPool(poolPath.toUri().toURL());

        // Verify clean server
        List<PatchId> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        PatchSet latest = server.getLatestPatch();
        Assert.assertNull("Latest is null", latest);
        
        // Obtain the smart patch from the pool
        SmartPatch smartPatch = pool.getSmartPatch(latest, PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), smartPatch.getPatchId());
        Assert.assertEquals(poolPath.resolve("foo-1.0.0.zip").toAbsolutePath(), smartPatch.getPatchFile().toPath());
        Assert.assertTrue("Patch set empty", smartPatch.getRemoveSet().isEmpty());
        Assert.assertTrue("Patch set empty", smartPatch.getReplaceSet().isEmpty());
        
        Path path1 = Paths.get("config/propsA.properties");
        Path path2 = Paths.get("config/removeme.properties");
        Path path3 = Paths.get("lib/foo-1.0.0.jar");
        Path path4 = Paths.get("lib/foo-1.1.0.jar");
        
        // Verify the add set
        Assert.assertEquals(3, smartPatch.getAddSet().size());
        Assert.assertTrue(smartPatch.isAddPath(path1));
        Assert.assertTrue(smartPatch.isAddPath(path2));
        Assert.assertTrue(smartPatch.isAddPath(path3));
        
        // Update the server with a known patch
        PatchSet patch = server.applySmartPatch(smartPatch);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());

        // Verify server files
        Assert.assertEquals(3, patch.getArtefacts().size());
        Iterator<ArtefactId> itpatch = patch.getArtefacts().iterator();
        Assert.assertEquals(path1, itpatch.next().getPath());
        File file = targetPath.resolve(path1).toFile();
        Assert.assertTrue("File exists: " + file, file.exists());
        Assert.assertEquals(path2, itpatch.next().getPath());
        file = targetPath.resolve(path2).toFile();
        Assert.assertTrue("File exists: " + file, file.exists());
        Assert.assertEquals(path3, itpatch.next().getPath());
        file = targetPath.resolve(path3).toFile();
        Assert.assertTrue("File exists: " + file, file.exists());
        
        // Verify latest server patch
        latest = server.getLatestPatch();
        Assert.assertNotNull("Latest not null", latest);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), latest.getPatchId());
        Assert.assertEquals(3, latest.getArtefacts().size());
        itpatch = latest.getArtefacts().iterator();
        Assert.assertEquals(path1, itpatch.next().getPath());
        Assert.assertEquals(path2, itpatch.next().getPath());
        Assert.assertEquals(path3, itpatch.next().getPath());
        
        // Obtain the same smart patch from the pool
        smartPatch = pool.getSmartPatch(latest, PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), smartPatch.getPatchId());
        Assert.assertEquals(poolPath.resolve("foo-1.0.0.zip").toAbsolutePath(), smartPatch.getPatchFile().toPath());
        Assert.assertEquals("Zero to remove", 0, smartPatch.getRemoveSet().size());
        Assert.assertEquals("Zero to replace", 0, smartPatch.getReplaceSet().size());
        Assert.assertEquals("Zero to add", 0, smartPatch.getAddSet().size());

        // Nothing to do on empty smart patch
        patch = server.applySmartPatch(smartPatch);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
        Assert.assertEquals(3, patch.getArtefacts().size());

        // Obtain the latest smart patch from the pool
        smartPatch = pool.getSmartPatch(latest, null);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), smartPatch.getPatchId());
        Assert.assertEquals(poolPath.resolve("foo-1.1.0.zip").toAbsolutePath(), smartPatch.getPatchFile().toPath());
        Assert.assertEquals("Two to remove", 2, smartPatch.getRemoveSet().size());
        Assert.assertTrue(smartPatch.isRemovePath(path2));
        Assert.assertTrue(smartPatch.isRemovePath(path3));
        Assert.assertEquals("One to replace", 1, smartPatch.getReplaceSet().size());
        Assert.assertTrue(smartPatch.isReplacePath(path1));
        Assert.assertEquals("One to add", 1, smartPatch.getAddSet().size());
        Assert.assertTrue(smartPatch.isAddPath(path4));

        // Update the server with the latest patch
        patch = server.applySmartPatch(smartPatch);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patch.getPatchId());
        Assert.assertEquals(2, patch.getArtefacts().size());
        Assert.assertTrue(patch.containsPath(path1));
        Assert.assertTrue(patch.containsPath(path4));

        // Verify server files
        itpatch = patch.getArtefacts().iterator();
        Assert.assertEquals(path1, itpatch.next().getPath());
        file = targetPath.resolve(path1).toFile();
        Assert.assertTrue("File exists: " + file, file.exists());
        Assert.assertEquals(path4, itpatch.next().getPath());
        file = targetPath.resolve(path4).toFile();
        Assert.assertTrue("File exists: " + file, file.exists());
        file = targetPath.resolve(path2).toFile();
        Assert.assertFalse("File does not exists: " + file, file.exists());
        file = targetPath.resolve(path3).toFile();
        Assert.assertFalse("File does not exists: " + file, file.exists());
        
        // Verify latest server patch
        latest = server.getLatestPatch();
        Assert.assertNotNull("Latest not null", latest);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), latest.getPatchId());
        Assert.assertEquals(2, latest.getArtefacts().size());
        itpatch = latest.getArtefacts().iterator();
        Assert.assertEquals(path1, itpatch.next().getPath());
        Assert.assertEquals(path4, itpatch.next().getPath());
    }
}
