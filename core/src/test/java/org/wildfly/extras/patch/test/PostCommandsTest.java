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
import java.util.List;

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
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.Main;
import org.wildfly.extras.patch.utils.IOUtils;

public class PostCommandsTest {

    final static Path serverPath = Paths.get("target/servers/PostCommandsTest/srvA");
    final static Path[] repoPaths = new Path[3];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPath);
        serverPath.toFile().mkdirs();
        for (int i = 0; i < repoPaths.length; i++) {
            repoPaths[i] = Paths.get("target/repos/PostCommandsTest/repo" + (i + 1));
            IOUtils.rmdirs(repoPaths[i]);
            repoPaths[i].toFile().mkdirs();
        }
    }

    @Test
    public void testPostCommands() throws Exception {

        URL repoURL = repoPaths[0].toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPath).build();
        Server server = patchTool.getServer();
        Repository repo = patchTool.getRepository();
        
        PatchId pid100 = PatchId.fromURL(Archives.getZipUrlFoo100());
        PatchMetadata md100 = new PatchMetadataBuilder().patchId(pid100).postCommands("echo Do first", "echo Do after").build();
        DataHandler data100 = new DataHandler(new URLDataSource(Archives.getZipUrlFoo100()));
        repo.addArchive(md100, data100, false);

        // Verify clean server
        List<PatchId> patches = server.queryAppliedPatches();
        Assert.assertTrue("Patch set empty", patches.isEmpty());
        
        // Obtain the smart patch from the repo
        SmartPatch smartPatch = repo.getSmartPatch(null, PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), smartPatch.getPatchId());
        
        // Verify post install commands
        List<String> cmds = smartPatch.getMetadata().getPostCommands();
        Assert.assertEquals(2, cmds.size());
        Assert.assertEquals("echo Do first", cmds.get(0));
        Assert.assertEquals("echo Do after", cmds.get(1));
        
        // Update the server with a known patch
        Patch patch = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
    }

    @Test
    public void testAddThroughMainWithCmd() throws Exception {

        String fileUrl = Archives.getZipUrlFoo100().toString();
        String repoUrl = repoPaths[1].toUri().toURL().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add", fileUrl, "--add-cmd", "echo hello world"});
        
        URL repoURL = repoPaths[1].toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        Repository repo = patchTool.getRepository();
        
        Patch patch = repo.getPatch(PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(1, patch.getMetadata().getPostCommands().size());
        Assert.assertEquals("echo hello world", patch.getMetadata().getPostCommands().get(0));
    }

    @Test
    public void testAddThroughMainWithMetadata() throws Exception {

        String fileUrl = Archives.getZipUrlFoo100().toString();
        String repoUrl = repoPaths[2].toUri().toURL().toString();
        String metadataUrl = Paths.get("src/test/resources/simple-metadata.xml").toUri().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add", fileUrl, "--metadata", metadataUrl});
        
        URL repoURL = repoPaths[2].toFile().toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        Repository repo = patchTool.getRepository();
        
        Patch patch = repo.getPatch(PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(1, patch.getMetadata().getPostCommands().size());
        Assert.assertEquals("echo hello world", patch.getMetadata().getPostCommands().get(0));
    }
}
