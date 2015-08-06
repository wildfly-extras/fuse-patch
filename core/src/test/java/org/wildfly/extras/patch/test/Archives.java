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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.wildfly.extras.patch.Package;
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
        File targetFile = Paths.get("target/foo-1.0.0.zip").toFile();
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
        File targetFile = Paths.get("target/foo-1.0.0.SP1.zip").toFile();
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
        File targetFile = Paths.get("target/foo-1.1.0.zip").toFile();
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
        File targetFile = Paths.get("target/bar-1.0.0.zip").toFile();
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

    static void assertPathsEqual(final Package expSet, final Path rootPath) throws IOException {
        final Set<Path> expPaths = new HashSet<>();
        for (Record rec : expSet.getRecords()) {
            expPaths.add(rec.getPath());
        }
        final Set<Path> wasPaths = new HashSet<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Path relpath = rootPath.relativize(path);
                if (!relpath.startsWith("fusepatch")) {
                    wasPaths.add(relpath);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Assert.assertEquals(expPaths, wasPaths);
    }

    static void assertPathsEqual(List<Record> exp, List<Record> was) {
        final Set<Path> expPaths = new HashSet<>();
        for (Record rec : exp) {
            expPaths.add(rec.getPath());
        }
        final Set<Path> wasPaths = new HashSet<>();
        for (Record rec : was) {
            wasPaths.add(rec.getPath());
        }
        Assert.assertEquals(expPaths, wasPaths);
    }
}
