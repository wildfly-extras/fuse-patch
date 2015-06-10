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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.fuse.patch.ArtefactId;
import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.Version;
import com.redhat.fuse.patch.internal.WildFlyServerInstance;
import com.redhat.fuse.patch.utils.IOUtils;

public class QueryServerTest {

    final static Path targetPath = Paths.get("target/servers/serverA");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(targetPath);
        IOUtils.copydirs(targetPath, Paths.get("src/test/etc/wildfly"));
    }

    @Test
    public void testQueryServer() throws Exception {

        WildFlyServerInstance server = new WildFlyServerInstance(targetPath);
        List<PatchId> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // update the patch set
        PatchId patchId = PatchId.create("fuse", Version.parseVersion("1.0.0"));
        PatchSet patchSet = new PatchSet(patchId, Collections.<ArtefactId>emptySet());
        server.updatePatchSet(patchSet);

        // verify that we can query the set
        patches = server.queryAppliedPatches();
        Assert.assertEquals("Patch not empty", 1, patches.size());
        Assert.assertEquals(patchId, patches.get(0));
        
        // verify that we can query the latest
        patchSet = server.getLatestPatch();
        Assert.assertEquals(patchId, patchSet.getPatchId());
    }
}
