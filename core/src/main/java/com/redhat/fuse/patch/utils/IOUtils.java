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
package com.redhat.fuse.patch.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class IOUtils {

    /**
     * Writing the specified contents to the specified OutputStream. Flushing the stream when
     * completed. Caller is responsible for opening and closing the specified stream.
     *
     * @param output The OutputStream
     * @param content The content to write to the specified stream
     * @throws IOException If a problem occured during any I/O operations
     */
    public static void writeWithFlush(final OutputStream output, final byte[] content) throws IOException {
        IllegalArgumentAssertion.assertNotNull(output, "output");
        IllegalArgumentAssertion.assertNotNull(content, "content");
        final int size = 4096;
        int offset = 0;
        while (content.length - offset > size) {
            output.write(content, offset, size);
            offset += size;
        }
        output.write(content, offset, content.length - offset);
        output.flush();
    }

    public static void copydirs(final Path targetDir, final Path sourceDir) throws IOException {
        IllegalArgumentAssertion.assertNotNull(targetDir, "targetDir");
        IllegalArgumentAssertion.assertNotNull(sourceDir, "sourceDir");
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetdir = targetDir.resolve(dir.relativize(sourceDir));
                targetdir.toFile().mkdirs();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void rmdirs(final Path targetDir) throws IOException {
        IllegalArgumentAssertion.assertNotNull(targetDir, "targetDir");
        if (targetDir.toFile().exists()) {
            Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
