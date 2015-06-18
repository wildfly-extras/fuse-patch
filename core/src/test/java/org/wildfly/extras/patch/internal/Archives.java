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
package org.wildfly.extras.patch.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchSet.Action;
import org.wildfly.extras.patch.PatchSet.Record;
import org.wildfly.extras.patch.internal.Parser;
import org.wildfly.extras.patch.test.subA.ClassA;

public class Archives {

    public static File getZipFileA() {
        File targetFile = Paths.get("target/foo-1.0.0.zip").toFile();
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/removeme.properties");
            archive.add(new FileAsset(new File("src/test/resources/propsA1.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile;
    }

    public static File getZipFileB() {
        File targetFile = Paths.get("target/foo-1.1.0.zip").toFile();
        if (!targetFile.exists()) {
            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
            jar.addClasses(ClassA.class);
            GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
            archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
            archive.add(new FileAsset(new File("src/test/resources/propsA2.properties")), "config/propsA.properties");
            archive.as(ZipExporter.class).exportTo(targetFile, true);
        }
        return targetFile;
    }
    
    public static PatchSet getPatchSetA() throws IOException {
        File zipFile = getZipFileA();
        return Parser.buildPatchSetFromZip(PatchId.fromFile(zipFile), Action.ADD, zipFile);
    }
    
    public static PatchSet getPatchSetB() throws IOException {
        File zipFile = getZipFileB();
        return Parser.buildPatchSetFromZip(PatchId.fromFile(zipFile), Action.ADD, zipFile);
    }

    public static void assertEqualActionPath(String line, Record was) {
        Record exp = Record.fromString(line + " 0");
        Assert.assertEquals(exp.getAction() + " " + exp.getPath(), was.getAction() + " " + was.getPath());
    }

}
