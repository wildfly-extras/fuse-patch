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
import java.net.URLEncoder;
import java.nio.channels.FileChannel;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.utils.IOUtils;

public class LocalRepositoryTest extends AbstractRepositoryTest {

    @BeforeClass
    public static void setUp() throws Exception {
        repoURL = new URL[6];
        for (int i = 0; i < repoURL.length; i++) {
            File path = new File("target/repos/LocalRepositoryTest/repo" + (i + 1));
            repoURL[i] = path.toURI().toURL();
            IOUtils.rmdirs(path);
            path.mkdirs();
        }
    }

    PatchTool getPatchTool(URL repoURL) {
        return new PatchToolBuilder().repositoryURL(repoURL).build();
    }

    @Override
    boolean isRemoveSupported() {
        return true;
    }

    @Test
    public void testRepoUrlWithSpaces() throws Exception {

        File path = new File("target/repos/LocalRepositoryTest", "repo && path");
        IOUtils.rmdirs(path);
        path.mkdirs();

        URL repoURL = new URL("file:./target/repos/LocalRepositoryTest/" + URLEncoder.encode("repo && path", "UTF-8"));
        PatchTool patchTool = new PatchToolBuilder().repositoryURL(repoURL).build();
        Repository repo = patchTool.getRepository();

        // Add archive foo-1.0.0
        PatchId patchId = repo.addArchive(Archives.getZipUrlFoo100());
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patchId);
    }

    @Test
    public void testFileMove() throws Exception {

        PatchTool patchTool = getPatchTool(repoURL[5]);
        Repository repo = patchTool.getRepository();

        // copy a file to the root of the repository
        File zipPathA = new File(Archives.getZipUrlFoo100().toURI());
        File targetFile = new File(new File(repoURL[5].toURI()), zipPathA.getName());
        FileChannel input = new FileInputStream(zipPathA).getChannel();
        try {
            FileChannel output = new FileOutputStream(targetFile).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }

        PatchId patchId = repo.addArchive(targetFile.toURI().toURL());
        Patch patch = repo.getPatch(patchId);
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patch.getPatchId());
        Assert.assertEquals(4, patch.getRecords().size());

        // Verify that the file got removed
        Assert.assertFalse("File got removed", targetFile.exists());
    }
}
