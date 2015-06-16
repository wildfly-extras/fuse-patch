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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchRepository;
import com.redhat.fuse.patch.internal.DefaultPatchRepository;
import com.redhat.fuse.patch.test.subA.ClassA;
import com.redhat.fuse.patch.utils.IOUtils;

public class QueryRepositoryTest {

    final static Path repoPathA = Paths.get("target/repos/qrtA");
    final static Path repoPathB = Paths.get("target/repos/qrtB");

    @BeforeClass
    public static void setUp() throws Exception {
        IOUtils.rmdirs(repoPathA);
        IOUtils.rmdirs(repoPathB);
        repoPathA.toFile().mkdirs();
        repoPathB.toFile().mkdirs();
    }

    @Test
    public void testRelativeRepositoryUrl() throws Exception {

        PatchRepository repo = new DefaultPatchRepository(new URL("file:./target/repos/qrtA"));
        repo.addArchive(getRepoContentA());
        repo.addArchive(getRepoContentB());
        
        List<PatchId> patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 2, patches.size());
    }

    @Test
    public void testQueryRepository() throws Exception {
        
        PatchRepository repo = new DefaultPatchRepository(repoPathB.toUri().toURL());
        repo.addArchive(getRepoContentA());
        repo.addArchive(getRepoContentB());
        
        List<PatchId> patches = repo.queryAvailable(null);
        Assert.assertEquals("Patch available", 2, patches.size());
        
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patches.get(1));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), repo.getLatestAvailable("foo"));
        Assert.assertNull(repo.getLatestAvailable("bar"));
    }

    static Path getRepoContentA() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/removeme.properties");
        archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
        Path resultPath = Paths.get("target/foo-1.0.0.zip");
        archive.as(ZipExporter.class).exportTo(resultPath.toFile(), true);
        return resultPath;
    }

    static Path getRepoContentB() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
        Path resultPath = Paths.get("target/foo-1.1.0.zip");
        archive.as(ZipExporter.class).exportTo(resultPath.toFile(), true);
        return resultPath;
    }
}
