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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

public class SimpleRepositoryTest {

    final static Path repoPathA = Paths.get("target/repos/SimpleRepositoryTest/repoA");
    final static Path repoPathB = Paths.get("target/repos/SimpleRepositoryTest/repoB");
    final static Path repoPathC = Paths.get("target/repos/SimpleRepositoryTest/repoC");
    final static Path repoPathD = Paths.get("target/repos/SimpleRepositoryTest/repoD");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPathA);
        IOUtils.rmdirs(repoPathB);
        IOUtils.rmdirs(repoPathC);
        IOUtils.rmdirs(repoPathD);
        repoPathA.toFile().mkdirs();
        repoPathB.toFile().mkdirs();
        repoPathC.toFile().mkdirs();
        repoPathD.toFile().mkdirs();
    }

    @Test
    public void testSimpleAccess() throws Exception {
        
        URL urlA = new URL("file:./target/repos/SimpleRepositoryTest/repoA");
        PatchTool patchTool = new PatchToolBuilder().repositoryUrl(urlA).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        PatchSet patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());
        
        patchId = repo.addArchive(Archives.getZipUrlFoo110());
        repo.addPostCommand(patchId, new String[]{"bin/fusepatch.sh", "--query-server" });
        patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patchSet.getPatchId());
        Assert.assertEquals(3, patchSet.getRecords().size());
        Assert.assertEquals(1, patchSet.getPostCommands().size());
        Assert.assertEquals("bin/fusepatch.sh --query-server", patchSet.getPostCommands().get(0));
        
        List<PatchId> patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 2, patches.size());
        
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(1));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.getLatestAvailable("foo"));
        Assert.assertNull(repo.getLatestAvailable("bar"));
    }

    @Test
    public void testFileMove() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathB).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        // copy a file to the root of the repository
        File zipFileA = Archives.getZipFileFoo100();
        File targetFile = repoPathB.resolve(zipFileA.getName()).toFile();
        Files.copy(zipFileA.toPath(), targetFile.toPath());
        
        PatchId patchId = repo.addArchive(targetFile.toURI().toURL());
        PatchSet patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());

        // Verify that the file got removed
        Assert.assertFalse("File got removed", targetFile.exists());
    }

    @Test
    public void testOverlappingPaths() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathC).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        repo.addArchive(Archives.getZipUrlFoo100());
        Path copyPath = Paths.get("target/foo-copy-1.1.0.zip");
        Files.copy(Archives.getZipFileFoo110().toPath(), copyPath, REPLACE_EXISTING);
        try {
            repo.addArchive(copyPath.toUri().toURL());
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            Assert.assertTrue(ex.getMessage().contains("duplicate paths in [foo-1.0.0]"));
        }
    }

    @Test
    public void testAddOneOff() throws Exception {
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathD).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        PatchId oneoffId = PatchId.fromString("foo-1.0.0");
        try {
            repo.addArchive(Archives.getZipUrlFoo100SP1(), oneoffId);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            // expected
        }
        
        PatchId idA = repo.addArchive(Archives.getZipUrlFoo100());
        PatchSet setA = repo.getPatchSet(idA);
        PatchId idB = repo.addArchive(Archives.getZipUrlFoo100SP1(), oneoffId);
        PatchSet setB = repo.getPatchSet(idB);
        Archives.assertPathsEqual(setA.getRecords(), setB.getRecords());
        Assert.assertEquals(1, setB.getDependencies().size());
        Assert.assertEquals(idA, setB.getDependencies().iterator().next());
        
        PatchSet smartSet = PatchSet.smartSet(setA, setB);
        Assert.assertEquals(1, smartSet.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartSet.getRecords().get(0));
    }
}
