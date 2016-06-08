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
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.utils.IOUtils;

public class PostCommandsTest {

    final static File serverPath = new File("target/servers/PostCommandsTest/srvA");
    final static File[] repoPaths = new File[3];

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(serverPath);
        serverPath.mkdirs();
        for (int i = 0; i < repoPaths.length; i++) {
            repoPaths[i] = new File("target/repos/PostCommandsTest/repo" + (i + 1));
            IOUtils.rmdirs(repoPaths[i]);
            repoPaths[i].mkdirs();
        }
    }

    @Test
    public void testPostCommands() throws Exception {

        URL repoURL = repoPaths[0].toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).serverPath(serverPath).build();
        Server server = patchTool.getServer();
        Repository repo = patchTool.getRepository();
        
        String[] cmdarr = new String[] {"echo first", "echo after"};
        if (LocalFileRepository.isWindows()) {
        	cmdarr[0] = "cmd /c " + cmdarr[0];
        	cmdarr[1] = "cmd /c " + cmdarr[1];
        }
        
        PatchId pid100 = PatchId.fromURL(Archives.getZipUrlFoo100());
        PatchMetadata md100 = new PatchMetadataBuilder().patchId(pid100).postCommands(cmdarr).build();
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
        Assert.assertEquals(cmdarr[0], cmds.get(0));
        Assert.assertEquals(cmdarr[1], cmds.get(1));
        
        // Update the server with a known patch
        Patch patch = server.applySmartPatch(smartPatch, false);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
    }

    @Test
    public void testAddThroughMainWithCmd() throws Throwable {

        String fileUrl = Archives.getZipUrlFoo100().toString();
        String repoUrl = repoPaths[1].toURI().toURL().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add", fileUrl, "--add-cmd", "echo hello world"});
        
        URL repoURL = repoPaths[1].toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        Repository repo = patchTool.getRepository();
        
        Patch patch = repo.getPatch(PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(1, patch.getMetadata().getPostCommands().size());
        Assert.assertEquals("echo hello world", patch.getMetadata().getPostCommands().get(0));
    }

    @Test
    public void testAddThroughMainWithMetadata() throws Throwable {

        String fileUrl = Archives.getZipUrlFoo100().toString();
        String repoUrl = repoPaths[2].toURI().toURL().toString();
        String metadataUrl = new File("src/test/resources/simple-metadata.xml").toURI().toString();
        Main.mainInternal(new String[] {"--repository", repoUrl, "--add", fileUrl, "--metadata", metadataUrl});
        
        URL repoURL = repoPaths[2].toURI().toURL();
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        Repository repo = patchTool.getRepository();
        
        Patch patch = repo.getPatch(PatchId.fromString("foo-1.0.0"));
        Assert.assertEquals(1, patch.getMetadata().getPostCommands().size());
        Assert.assertEquals("echo hello world", patch.getMetadata().getPostCommands().get(0));
    }
}
