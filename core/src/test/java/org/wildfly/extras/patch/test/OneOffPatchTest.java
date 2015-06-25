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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.utils.IOUtils;

public class OneOffPatchTest {

    final static Path serverPathA = Paths.get("target/servers/OneOffPatchTest/srvA");
    final static Path repoPathA = Paths.get("target/repos/OneOffPatchTest/repoA");

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
        Repository repo = patchTool.getPatchRepository();
        
        PatchId idA = repo.addArchive(Archives.getZipUrlFoo100());
        PatchId idB = repo.addArchive(Archives.getZipUrlFoo100SP1(), idA);

        Package setA = patchTool.install(idA, false);
        Assert.assertEquals(4, setA.getRecords().size());
        Archives.assertActionPathEquals("INFO config/propsA.properties", setA.getRecords().get(0));
        Archives.assertActionPathEquals("INFO config/propsB.properties", setA.getRecords().get(1));
        Archives.assertActionPathEquals("INFO config/remove-me.properties", setA.getRecords().get(2));
        Archives.assertActionPathEquals("INFO lib/foo-1.0.0.jar", setA.getRecords().get(3));

        Package setB = patchTool.install(idB, false);
        Assert.assertEquals(4, setB.getRecords().size());
        Archives.assertActionPathEquals("INFO config/propsA.properties", setB.getRecords().get(0));
        Archives.assertActionPathEquals("INFO config/propsB.properties", setB.getRecords().get(1));
        Archives.assertActionPathEquals("INFO config/remove-me.properties", setB.getRecords().get(2));
        Archives.assertActionPathEquals("INFO lib/foo-1.0.0.jar", setB.getRecords().get(3));
    }
}
