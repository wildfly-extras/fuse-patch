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
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.utils.IOUtils;


public class OneOffPatchTest {

    final static File[] serverPaths = new File[2];
    final static File repoPath = new File("target/repos/OneOffPatchTest/repo");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPath);
        repoPath.mkdirs();
        for (int i = 0; i < 2; i++) {
            serverPaths[i] = new File("target/repos/OneOffPatchTest/srv" + (i + 1));
            IOUtils.rmdirs(serverPaths[i]);
            serverPaths[i].mkdirs();
        }
        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        PatchId oneoffId = patchTool.getRepository().addArchive(Archives.getZipUrlFoo100());
        URL url100sp1 = Archives.getZipUrlFoo100SP1();
        PatchId pid100sp1 = PatchId.fromURL(url100sp1);
        PatchMetadata md100sp1 = new PatchMetadataBuilder().patchId(pid100sp1).oneoffId(oneoffId).build();
        DataHandler data100sp1 = new DataHandler(new URLDataSource(url100sp1));
        patchTool.getRepository().addArchive(md100sp1, data100sp1, false);
    }

    @Test
    public void testSimpleOneOff() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[0]).build();
        
        PatchId pid100 = PatchId.fromURL(Archives.getZipUrlFoo100());
        PatchId pid100sp1 = PatchId.fromURL(Archives.getZipUrlFoo100SP1());
        File filePath = new File(serverPaths[0], "config/propsA.properties");
        
        Patch pack100 = patchTool.install(pid100, false);
        Assert.assertEquals("A1", readProperty("some.prop", filePath));
        
        Patch pack100sp1 = patchTool.install(pid100sp1, false);
        Assert.assertEquals("A2", readProperty("some.prop", filePath));
        Archives.assertPathsEqual(pack100.getRecords(), pack100sp1.getRecords());
    }

    @Test
    public void testOneOffWithoutPriorBase() throws Exception {

        URL repoURL = repoPath.toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPaths[1]).build();
        
        PatchId pid100sp1 = PatchId.fromURL(Archives.getZipUrlFoo100SP1());
        File filePath = new File(serverPaths[1], "config/propsA.properties");
        
        Patch pack100sp1 = patchTool.install(pid100sp1, false);
        Assert.assertEquals("A2", readProperty("some.prop", filePath));
        Archives.assertActionPathEquals("INFO config/propsA.properties", pack100sp1.getRecords().get(0));
        Archives.assertActionPathEquals("INFO config/propsB.properties", pack100sp1.getRecords().get(1));
        Archives.assertActionPathEquals("INFO config/remove-me.properties", pack100sp1.getRecords().get(2));
        Archives.assertActionPathEquals("INFO lib/foo-1.0.0.jar", pack100sp1.getRecords().get(3));
    }

    private String readProperty(String key, File path) throws IOException {
        Properties properties = new Properties();
        FileInputStream fis = new FileInputStream(path);
        try {
            properties.load(fis);
        } finally {
            fis.close();
        }
        return properties.getProperty(key);
    }
}
