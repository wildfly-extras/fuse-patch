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
import org.wildfly.extras.patch.Identity;
import org.wildfly.extras.patch.PatchRepository;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.ServerInstance;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.Main;
import org.wildfly.extras.patch.utils.IOUtils;

public class PostCommandsTest {

    final static Path serverPathA = Paths.get("target/servers/PostCommandsTest/srvA");
    final static Path repoPathA = Paths.get("target/repos/PostCommandsTest/repoA");
    final static Path repoPathB = Paths.get("target/repos/PostCommandsTest/repoB");
    final static Path repoPathC = Paths.get("target/repos/PostCommandsTest/repoC");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPathA);
        IOUtils.rmdirs(repoPathA);
        IOUtils.rmdirs(repoPathB);
        IOUtils.rmdirs(repoPathC);
        serverPathA.toFile().mkdirs();
        repoPathA.toFile().mkdirs();
        repoPathB.toFile().mkdirs();
        repoPathC.toFile().mkdirs();
    }

    @Test
    public void testPostCommands() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathA).serverPath(serverPathA).build();
        ServerInstance server = patchTool.getServerInstance();
        PatchRepository repo = patchTool.getPatchRepository();
        
        repo.addArchive(Archives.getZipUrlFoo100());
        repo.addPostCommand(Identity.fromString("foo-1.0.0"), new String[]{"echo", "Do", "first"});
        repo.addPostCommand(Identity.fromString("foo-1.0.0"), new String[]{"echo", "Do", "after"});

        // Verify clean server
        List<Identity> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Obtain the smart patch from the repo
        SmartPatch smartPatch = repo.getSmartPatch(null, Identity.fromString("foo-1.0.0"));
        Assert.assertEquals(Identity.fromString("foo-1.0.0"), smartPatch.getPatchId());
        
        // Verify post install commands
        List<String> cmds = smartPatch.getPostCommands();
        Assert.assertEquals(2, cmds.size());
        Assert.assertEquals("echo Do first", cmds.get(0));
        Assert.assertEquals("echo Do after", cmds.get(1));
        
        // Update the server with a known patch
        Package patch = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(Identity.fromString("foo-1.0.0"), patch.getPatchId());
    }

    @Test
    public void testAddWithCmd() throws Exception {

        String fileUrl = Archives.getZipUrlFoo100().toString();
        String repoUrl = repoPathB.toUri().toURL().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add", fileUrl, "--add-cmd", "echo hello world"});
        
        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathB).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        Package patchSet = repo.getPatchSet(Identity.fromString("foo-1.0.0"));
        Assert.assertEquals(1, patchSet.getPostCommands().size());
        Assert.assertEquals("echo hello world", patchSet.getPostCommands().get(0));
    }

    @Test
    public void testAddWithExisting() throws Exception {

        PatchTool patchTool = new PatchToolBuilder().repositoryPath(repoPathC).build();
        PatchRepository repo = patchTool.getPatchRepository();
        
        Identity patchId = repo.addArchive(Archives.getZipUrlFoo100());
        
        String repoUrl = repoPathC.toUri().toURL().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add-cmd", "foo-1.0.0", "echo hello world"});
        
        Package patchSet = repo.getPatchSet(patchId);
        Assert.assertEquals(1, patchSet.getPostCommands().size());
        Assert.assertEquals("echo hello world", patchSet.getPostCommands().get(0));
    }
}
