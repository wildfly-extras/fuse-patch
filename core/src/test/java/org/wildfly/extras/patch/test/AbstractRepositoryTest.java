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
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.utils.IOUtils;

public abstract class AbstractRepositoryTest {

    static URL[] repoURL = new URL[5];;

    abstract PatchTool getPatchTool(URL repoURL);
    
    abstract boolean isRemoveSupported();
    
    @Test
    public void testSimpleAccess() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[0]);
        Repository repo = patchTool.getRepository();

        // Add archive foo-1.0.0
        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        Patch patch = repo.getPatch(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
        Assert.assertEquals(4, patch.getRecords().size());

        // Add archive foo-1.1.0
        patchId = PatchId.fromString("foo-1.1.0");
        PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).postCommands("bin/fusepatch.sh --query-server").build();
        DataHandler dataHandler = new DataHandler(new URLDataSource(Archives.getZipUrlFoo110()));
        patchId = repo.addArchive(metadata, dataHandler, false);
        patch = repo.getPatch(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patch.getPatchId());
        Assert.assertEquals(3, patch.getRecords().size());
        Assert.assertEquals(1, patch.getMetadata().getPostCommands().size());
        Assert.assertEquals("bin/fusepatch.sh --query-server", patch.getMetadata().getPostCommands().get(0));

        // Add archive foo-1.1.0 again
        patchId = repo.addArchive(Archives.getZipUrlFoo110());
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patch.getPatchId());

        // Query available
        List<PatchId> patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 2, patches.size());

        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(1));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.getLatestAvailable("foo"));
        Assert.assertNull(repo.getLatestAvailable("bar"));

        if (isRemoveSupported()) {
            
            // Cannot remove non-existing archive
            try {
                repo.removeArchive(PatchId.fromString("xxx-1.0.0"));
                Assert.fail("PatchException expected");
            } catch (PatchException ex) {
                String message = ex.getMessage();
                Assert.assertTrue(message, message.contains("not exist: xxx-1.0.0"));
            }

            // Remove archive
            Assert.assertTrue(repo.removeArchive(PatchId.fromString("foo-1.1.0")));
            patches = repo.queryAvailable(null);
            Assert.assertEquals("Patch available", 1, patches.size());

            Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(0));
            Assert.assertEquals(PatchId.fromString("foo-1.0.0"), repo.getLatestAvailable("foo"));
        }
    }

    @Test
    public void testOverlappingPaths() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[1]);
        Repository repo = patchTool.getRepository();

        repo.addArchive(Archives.getZipUrlFoo100());
        File copyPath = new File("target/foo-copy-1.1.0.zip");
        FileChannel input = new FileInputStream(new File(Archives.getZipUrlFoo110().toURI())).getChannel();
        try {
            FileChannel output = new FileOutputStream(copyPath).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        URL fileUrl = copyPath.toURI().toURL();
        try {
            repo.addArchive(fileUrl);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("duplicate paths in [foo-1.0.0]"));
        }

        // Force
        PatchId patchId = PatchId.fromURL(fileUrl);
        patchId = repo.addArchive(fileUrl, true);
        Assert.assertEquals(PatchId.fromString("foo-copy-1.1.0"), patchId);
    }

    @Test
    public void testEqualOverlappingPaths() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[2]);
        Repository repo = patchTool.getRepository();

        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.addArchive(Archives.getZipUrlFoo110()));
        Assert.assertEquals(PatchId.fromString("bar-1.0.0"), repo.addArchive(Archives.getZipUrlBar100()));
    }

    @Test
    public void testAddOneOff() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[3]);
        Repository repo = patchTool.getRepository();

        URL url100sp1 = Archives.getZipUrlFoo100SP1();
        PatchId pid100sp1 = PatchId.fromURL(url100sp1);
        PatchId oneoffId = PatchId.fromString("foo-1.0.0");
        PatchMetadata md100sp1 = new PatchMetadataBuilder().patchId(pid100sp1).oneoffId(oneoffId).build();
        DataHandler data100sp1 = new DataHandler(new URLDataSource(url100sp1));

        try {
            repo.addArchive(md100sp1, data100sp1, false);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            // expected
        }

        PatchId pid100 = repo.addArchive(Archives.getZipUrlFoo100());
        Patch pack100 = repo.getPatch(pid100);
        repo.addArchive(md100sp1, data100sp1, false);
        Patch pack100sp1 = repo.getPatch(pid100sp1);
        Archives.assertPathsEqual(pack100.getRecords(), pack100sp1.getRecords());
        Assert.assertEquals(0, pack100sp1.getMetadata().getDependencies().size());

        Patch smartSet = Patch.smartDelta(pack100, pack100sp1);
        Assert.assertEquals(1, smartSet.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartSet.getRecords().get(0));
    }

    @Test
    public void testArchiveWithRoles() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[4]);
        Repository repo = patchTool.getRepository();

        URL url100 = Archives.getZipUrlFoo100();
        PatchId pid100 = PatchId.fromURL(url100);
        PatchMetadata md100 = new PatchMetadataBuilder().patchId(pid100).roles("FooRole").build();
        DataHandler data100 = new DataHandler(new URLDataSource(url100));

        Assert.assertEquals(pid100, repo.addArchive(md100, data100, false));
        Assert.assertEquals(md100, repo.getPatch(pid100).getMetadata());
    }
}
