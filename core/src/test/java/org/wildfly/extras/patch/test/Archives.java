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
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.test.subA.ClassA;

class Archives {

    /**
     * foo-1.0.0.zip
     * 
     * config/remove-me.properties
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.0.0.jar
     */
    static URL getZipUrlFoo100() throws IOException {
        File targetFile = new File("target/foo-1.0.0.zip");
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/remove-me.properties");
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
            archive.add(new FileAsset(new File("src/test/resources/propsB.properties")), "config/propsB.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }

    /**
     * foo-1.0.0.SP1.zip
     * 
     * config/propsA.properties
     */
    static URL getZipUrlFoo100SP1() throws IOException {
        File targetFile = new File("target/foo-1.0.0.SP1.zip");
        if (!targetFile.exists()) {
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }
    
    /**
     * foo-1.1.0.zip
     * 
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.1.0.jar
     */
    static URL getZipUrlFoo110() throws IOException {
        File targetFile = new File("target/foo-1.1.0.zip");
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
            archive.add(new FileAsset(new File("src/test/resources/propsB.properties")), "config/propsB.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }
    
    /**
     * bar-1.0.0.zip
     * 
     * config/propsB.properties
     * lib/bar-1.0.0.jar
     */
    static URL getZipUrlBar100() throws IOException {
        File targetFile = new File("target/bar-1.0.0.zip");
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "bar-1.0.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsB.properties")), "config/propsB.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile.toURI().toURL();
    }
    
    static void assertActionPathEquals(String line, Record was) {
        Record exp = Record.fromString(line + " 0");
        Assert.assertEquals(exp.getAction() + " " + exp.getPath(), was.getAction() + " " + was.getPath());
    }

    static void assertPathsEqual(final Patch expSet, final File rootPath) throws IOException {
        final Set<String> expPaths = new HashSet<String>();
        for (Record rec : expSet.getRecords()) {
            expPaths.add(rec.getPath().toString());
        }
        final Set<String> wasPaths = new HashSet<String>();
        
        LinkedList<File> dirs = new LinkedList<File>();
        dirs.push(rootPath);
        File dir;
        while ((dir = dirs.poll()) != null) {
            for (File sub : dir.listFiles()) {
                if (sub.isDirectory()) {
                    dirs.push(sub);
                } else {
                    String relpath = rootPath.toURI().relativize(sub.toURI()).toString();
                    if (!relpath.startsWith("fusepatch")) {
                        wasPaths.add(relpath);
                    }
                }
            }
        }
        Assert.assertEquals(expPaths, wasPaths);
    }

    static void assertPathsEqual(List<Record> exp, List<Record> was) {
        final Set<File> expPaths = new HashSet<File>();
        for (Record rec : exp) {
            expPaths.add(rec.getPath());
        }
        final Set<File> wasPaths = new HashSet<File>();
        for (Record rec : was) {
            wasPaths.add(rec.getPath());
        }
        Assert.assertEquals(expPaths, wasPaths);
    }
}
