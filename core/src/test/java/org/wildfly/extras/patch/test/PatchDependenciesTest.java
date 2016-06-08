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
import java.net.URL;
import java.util.Collections;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.utils.IOUtils;

public class PatchDependenciesTest {

    final static File repoPath = new File("target/repos/PatchDependenciesTest/repo");
    final static File serverPath = new File("target/servers/PatchDependenciesTest/srvA");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.mkdirs();
        IOUtils.rmdirs(serverPath);
        serverPath.mkdirs();
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        PatchId pid100 = patchTool.getRepository().addArchive(Archives.getZipUrlFoo100());
        URL url110 = Archives.getZipUrlFoo110();
        PatchId pid110 = PatchId.fromURL(url110);
        DataHandler data110 = new DataHandler(new URLDataSource(url110));
        PatchMetadata md110 = new PatchMetadataBuilder().patchId(pid110).dependencies(Collections.singleton(pid100)).build();
        patchTool.getRepository().addArchive(md110, data110, false);
    }

    @Test
    public void testSimpleDependency() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPath).build();
        
        PatchId idA = PatchId.fromURL(Archives.getZipUrlFoo100());
        PatchId idB = PatchId.fromURL(Archives.getZipUrlFoo110());

        Patch setB;
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
