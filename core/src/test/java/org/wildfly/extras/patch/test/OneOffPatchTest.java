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

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.PackageMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.utils.IOUtils;

public class OneOffPatchTest {

    final static Path repoPath = Paths.get("target/repos/OneOffPatchTest/repo");
    final static Path serverPath = Paths.get("target/servers/OneOffPatchTest/srvA");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.toFile().mkdirs();
        IOUtils.rmdirs(serverPath);
        serverPath.toFile().mkdirs();
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).build();
        PatchId oneoffId = patchTool.getRepository().addArchive(Archives.getZipUrlFoo100());
        URL url100sp1 = Archives.getZipUrlFoo100SP1();
        PatchId pid100sp1 = PatchId.fromURL(url100sp1);
        PackageMetadata md100sp1 = new PackageMetadataBuilder().patchId(pid100sp1).oneoffId(oneoffId).build();
        DataHandler data100sp1 = new DataHandler(new URLDataSource(url100sp1));
        patchTool.getRepository().addArchive(md100sp1, data100sp1, false);
    }

    @Test
    public void testSimpleOneOff() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPath).serverPath(serverPath).build();
        
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        PatchId idB = PatchId.fromURL(Archives.getZipUrlFoo100SP1());

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
