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
import java.util.Collections;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchRepository;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.utils.IOUtils;

public class PatchDependenciesTest {

    final static Path serverPathA = Paths.get("target/servers/PatchDependenciesTest/srvA");
    final static Path repoPathA = Paths.get("target/repos/PatchDependenciesTest/repoA");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPathA);
        IOUtils.rmdirs(repoPathA);
        serverPathA.toFile().mkdirs();
        repoPathA.toFile().mkdirs();
    }

    @Test
    public void testSimpleOneOff() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathA).serverPath(serverPathA).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        PatchId idA = repo.addArchive(Archives.getZipUrlFoo100());
        PatchId idB = repo.addArchive(Archives.getZipUrlFoo110(), null, Collections.singleton(PatchId.fromString("foo-1.0.0")));

        PatchSet setB;
        try {
            setB = patchTool.install(idB, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            // expected
        }
        
        patchTool.install(idA, false);
        setB = patchTool.update("foo", false);
        Assert.assertEquals(3, setB.getRecords().size());
        Archives.assertActionPathEquals("INFO config/propsA.properties", setB.getRecords().get(0));
        Archives.assertActionPathEquals("INFO config/propsB.properties", setB.getRecords().get(1));
        Archives.assertActionPathEquals("INFO lib/foo-1.1.0.jar", setB.getRecords().get(2));
    }
}
