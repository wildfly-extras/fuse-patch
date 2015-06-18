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

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchRepository;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.internal.Archives;
import com.redhat.fuse.patch.internal.DefaultPatchRepository;
import com.redhat.fuse.patch.utils.IOUtils;

public class SimpleRepositoryTest {

    final static Path repoPathA = Paths.get("target/repos/" + SimpleRepositoryTest.class.getSimpleName() + "/repoA");
    final static Path repoPathB = Paths.get("target/repos/" + SimpleRepositoryTest.class.getSimpleName() + "/repoB");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPathA);
        IOUtils.rmdirs(repoPathB);
        repoPathA.toFile().mkdirs();
        repoPathB.toFile().mkdirs();
    }

    @Test
    public void testRepository() throws Exception {
        
        PatchRepository repo = new DefaultPatchRepository(new URL("file:./target/repos/SimpleRepositoryTest/repoA"));
        
        PatchId patchId = repo.addArchive(Archives.getZipFileA().toURI().toURL());
        PatchSet patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(3, patchSet.getRecords().size());
        
        patchId = repo.addArchive(Archives.getZipFileB().toURI().toURL());
        repo.addPostCommand(patchId, "bin/fusepatch.sh --query-server");
        patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patchSet.getPatchId());
        Assert.assertEquals(2, patchSet.getRecords().size());
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
        
        PatchRepository repo = new DefaultPatchRepository(repoPathB.toUri().toURL());
        
        // copy a file to the root of the repository
        File zipFileA = Archives.getZipFileA();
        File targetFile = repoPathB.resolve(zipFileA.getName()).toFile();
        Files.copy(zipFileA.toPath(), targetFile.toPath());
        
        PatchId patchId = repo.addArchive(targetFile.toURI().toURL());
        PatchSet patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchSet.getPatchId());
        Assert.assertEquals(3, patchSet.getRecords().size());

        // Verify that the file got removed
        Assert.assertFalse("File got removed", targetFile.exists());
    }
}
