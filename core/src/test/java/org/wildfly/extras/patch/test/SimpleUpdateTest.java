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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.Archives;
import org.wildfly.extras.patch.internal.WildFlyServerInstance;
import org.wildfly.extras.patch.utils.IOUtils;

public class SimpleUpdateTest {

    final static Path serverPath = Paths.get("target/servers/" + SimpleUpdateTest.class.getSimpleName());

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPath);
        serverPath.toFile().mkdirs();
    }

    @Test
    public void testSimpleUpdate() throws Exception {

        WildFlyServerInstance server = new WildFlyServerInstance(serverPath);
        List<PatchId> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Verify smart patch A
        PatchSet setA = Archives.getPatchSetA();
        PatchId patchId = setA.getPatchId();
        SmartPatch smartPatch = new SmartPatch(setA, Archives.getZipFileA());
        Assert.assertEquals(patchId, smartPatch.getPatchId());
        Assert.assertEquals(setA.getRecords(), smartPatch.getRecords());
        
        // Apply smart patch A
        PatchSet curSet = server.applySmartPatch(smartPatch);
        Assert.assertEquals(patchId, curSet.getPatchId());
        Assert.assertEquals(setA.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setA.getRecords(), server.getPatchSet(patchId).getRecords());
        Archives.assertPathsEqual(setA, serverPath);

        // Verify smart patch B
        PatchSet setB = Archives.getPatchSetB();
        patchId = setB.getPatchId();
        PatchSet smartSet = PatchSet.smartSet(setA, setB);
        smartPatch = new SmartPatch(smartSet, Archives.getZipFileB());
        Assert.assertEquals(4, smartPatch.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartPatch.getRecords().get(0));
        Archives.assertActionPathEquals("DEL config/removeme.properties", smartPatch.getRecords().get(1));
        Archives.assertActionPathEquals("DEL lib/foo-1.0.0.jar", smartPatch.getRecords().get(2));
        Archives.assertActionPathEquals("ADD lib/foo-1.1.0.jar", smartPatch.getRecords().get(3));

        // Apply smart patch B
        curSet = server.applySmartPatch(smartPatch);
        Assert.assertEquals(patchId, curSet.getPatchId());
        Assert.assertEquals(2, curSet.getRecords().size());
        Assert.assertEquals(setB.getRecords(), curSet.getRecords());
        Archives.assertPathsEqual(setB.getRecords(), server.getPatchSet(patchId).getRecords());
        Archives.assertPathsEqual(setB, serverPath);
        
        // verify that we can query the set
        patches = server.queryAppliedPatches();
        Assert.assertEquals(1, patches.size());
        Assert.assertEquals(patchId, patches.get(0));
        Assert.assertEquals(patchId, server.getLatestApplied("foo"));
    }
}
