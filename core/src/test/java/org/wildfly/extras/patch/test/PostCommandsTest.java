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
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchRepository;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.Archives;
import org.wildfly.extras.patch.internal.DefaultPatchRepository;
import org.wildfly.extras.patch.internal.WildFlyServerInstance;
import org.wildfly.extras.patch.utils.IOUtils;

public class PostCommandsTest {

    final static Path serverPath = Paths.get("target/servers/" + PostCommandsTest.class.getSimpleName());
    final static Path repoPath = Paths.get("target/repos/" + PostCommandsTest.class.getSimpleName());

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPath);
        IOUtils.rmdirs(repoPath);
        serverPath.toFile().mkdirs();
        repoPath.toFile().mkdirs();
    }

    @Test
    public void testServerUpdate() throws Exception {

        WildFlyServerInstance server = new WildFlyServerInstance(serverPath);
        PatchRepository repo = new DefaultPatchRepository(repoPath.toUri().toURL());
        repo.addArchive(Archives.getZipUrlA());
        repo.addPostCommand(PatchId.fromString("foo-1.0.0"), "echo Do first");
        repo.addPostCommand(PatchId.fromString("foo-1.0.0"), "echo Do after");

        // Verify clean server
        List<PatchId> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Obtain the smart patch from the repo
        SmartPatch smartPatch = repo.getSmartPatch(null, PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), smartPatch.getPatchId());
        
        // Verify post install commands
        List<String> cmds = smartPatch.getPostCommands();
        Assert.assertEquals(2, cmds.size());
        Assert.assertEquals("echo Do first", cmds.get(0));
        Assert.assertEquals("echo Do after", cmds.get(1));
        
        // Update the server with a known patch
        PatchSet patch = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
    }
}
