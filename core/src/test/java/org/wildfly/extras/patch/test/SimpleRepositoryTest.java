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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchException;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.utils.IOUtils;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class SimpleRepositoryTest {

    final static Path[] repoPaths = new Path[5];

    @BeforeClass
    public static void setUp() throws Exception {
        for (int i = 0; i < 5; i++) {
            repoPaths[i] = Paths.get("target/repos/SimpleRepositoryTest/repo" + (i + 1));
            IOUtils.rmdirs(repoPaths[i]);
            repoPaths[i].toFile().mkdirs();
        }
    }

    @Test
    public void testSimpleAccess() throws Exception {

        URL urlA = new URL("file:./" + repoPaths[0].toString());

        PatchTool patchTool = new PatchToolBuilder().repositoryUrl(urlA).build();
        Repository repo = patchTool.getRepository();

        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        Package patchSet = repo.getPackage(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());

        patchId = repo.addArchive(Archives.getZipUrlFoo110());
        repo.addPostCommand(patchId, new String[] { "bin/fusepatch.sh", "--query-server" });
        patchSet = repo.getPackage(patchId);
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

        Assert.assertTrue(repo.removeArchive(PatchId.fromString("foo-1.1.0")));
        patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 1, patches.size());

        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), repo.getLatestAvailable("foo"));
    }

    @Test
    public void testFileMove() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[1]).build();
        Repository repo = patchTool.getRepository();

        // copy a file to the root of the repository
        File zipFileA = Archives.getZipFileFoo100();
        File targetFile = repoPaths[1].resolve(zipFileA.getName()).toFile();
        Files.copy(zipFileA.toPath(), targetFile.toPath());

        PatchId patchId = repo.addArchive(targetFile.toURI().toURL());
        Package patchSet = repo.getPackage(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(4, patchSet.getRecords().size());

        // Verify that the file got removed
        Assert.assertFalse("File got removed", targetFile.exists());
    }

    @Test
    public void testOverlappingPaths() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[2]).build();
        Repository repo = patchTool.getRepository();

        repo.addArchive(Archives.getZipUrlFoo100());
        Path copyPath = Paths.get("target/foo-copy-1.1.0.zip");
        Files.copy(Archives.getZipFileFoo110().toPath(), copyPath, REPLACE_EXISTING);
        try {
            repo.addArchive(copyPath.toUri().toURL());
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            String message = ex.getMessage();
            Assert.assertTrue(message, message.contains("duplicate paths in [foo-1.0.0]"));
        }

        // Force
        PatchId patchId = repo.addArchive(copyPath.toUri().toURL(), null, Collections.<PatchId> emptySet(), true);
        Assert.assertEquals(PatchId.fromString("foo-copy-1.1.0"), patchId);
    }

    @Test
    public void testEqualOverlappingPaths() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[3]).build();
        Repository repo = patchTool.getRepository();

        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.addArchive(Archives.getZipUrlFoo110()));
        Assert.assertEquals(PatchId.fromString("bar-1.0.0"), repo.addArchive(Archives.getZipUrlBar100()));
    }

    @Test
    public void testAddOneOff() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPaths[4]).build();
        Repository repo = patchTool.getRepository();

        PatchId oneoffId = PatchId.fromString("foo-1.0.0");
        try {
            repo.addArchive(Archives.getZipUrlFoo100SP1(), oneoffId);
            Assert.fail("PatchException expected");
        } catch (PatchException ex) {
            // expected
        }

        PatchId idA = repo.addArchive(Archives.getZipUrlFoo100());
        Package setA = repo.getPackage(idA);
        PatchId idB = repo.addArchive(Archives.getZipUrlFoo100SP1(), oneoffId);
        Package setB = repo.getPackage(idB);
        Archives.assertPathsEqual(setA.getRecords(), setB.getRecords());
        Assert.assertEquals(1, setB.getDependencies().size());
        Assert.assertEquals(idA, setB.getDependencies().iterator().next());

        Package smartSet = Package.smartSet(setA, setB);
        Assert.assertEquals(1, smartSet.getRecords().size());
        Archives.assertActionPathEquals("UPD config/propsA.properties", smartSet.getRecords().get(0));
    }
}
