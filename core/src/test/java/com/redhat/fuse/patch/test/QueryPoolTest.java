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
import com.redhat.fuse.patch.PatchPool;
import com.redhat.fuse.patch.internal.DefaultPatchPool;
import com.redhat.fuse.patch.test.subA.ClassA;

public class QueryPoolTest {

    final static Path poolPath = Paths.get("target/pools/poolA");

    @BeforeClass
    public static void setUp() throws Exception {
        setupPoolA(poolPath);
        setupPoolB(poolPath);
    }

    @Test
    public void testRelativePoolUrl() throws Exception {
        
        PatchPool pool = new DefaultPatchPool(new URL("file:./target/pools/poolA"));
        List<PatchId> patches = pool.queryAvailablePatches(null);
        Assert.assertEquals("Patch available", 2, patches.size());
    }

    @Test
    public void testQueryPool() throws Exception {
        
        PatchPool pool = new DefaultPatchPool(poolPath.toUri().toURL());
        List<PatchId> patches = pool.queryAvailablePatches(null);
        Assert.assertEquals("Patch available", 2, patches.size());
        
        Assert.assertEquals(PatchId.fromString("foo-1.0.0"), patches.get(0));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), patches.get(1));
        Assert.assertEquals(PatchId.fromString("foo-1.1.0"), pool.getLatestPatch("foo"));
        Assert.assertNull(pool.getLatestPatch("bar"));
    }

    static void setupPoolA(Path poolPath) {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/removeme.properties");
        archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
        poolPath.toFile().mkdirs();
        archive.as(ZipExporter.class).exportTo(poolPath.resolve("foo-1.0.0.zip").toFile(), true);
    }

    static void setupPoolB(Path poolPath) {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
        poolPath.toFile().mkdirs();
        archive.as(ZipExporter.class).exportTo(poolPath.resolve("foo-1.1.0.zip").toFile(), true);
    }
}
