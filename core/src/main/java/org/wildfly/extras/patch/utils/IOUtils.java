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
package org.wildfly.extras.patch.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.zip.CRC32;

public class IOUtils {

    public static void writeWithFlush(final byte[] content, final OutputStream output) throws IOException {
        IllegalArgumentAssertion.assertNotNull(content, "content");
        IllegalArgumentAssertion.assertNotNull(output, "output");
        final int size = 4096;
        int offset = 0;
        while (content.length - offset > size) {
            output.write(content, offset, size);
            offset += size;
        }
        output.write(content, offset, content.length - offset);
        output.flush();
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        IllegalArgumentAssertion.assertNotNull(input, "input");
        IllegalArgumentAssertion.assertNotNull(output, "output");
        byte[] bytes = new byte[4096];
        int read = input.read(bytes);
        while (read > 0) {
            output.write(bytes, 0, read);
            read = input.read(bytes);
        }
        output.flush();
    }

    public static void copydirs(final File targetDir, final File sourceDir) throws IOException {
        IllegalArgumentAssertion.assertNotNull(targetDir, "targetDir");
        IllegalArgumentAssertion.assertNotNull(sourceDir, "sourceDir");

        LinkedList<File> dirs = new LinkedList<File>();
        dirs.push(sourceDir);
        File dir;
        while ((dir = dirs.poll()) != null) {
            for (File sub : dir.listFiles()) {
                if (sub.isDirectory()) {
                    dirs.push(sub);
                    String relpath = sourceDir.toURI().relativize(sub.toURI()).toString();
                    new File(targetDir, relpath).mkdirs();
                }
            }
        }
    }

    public static void rmdirs(final File targetDir) throws IOException {
        IllegalArgumentAssertion.assertNotNull(targetDir, "targetDir");

        if (targetDir.exists()) {
            LinkedList<File> dirs = new LinkedList<File>();
            dirs.push(targetDir);
            LinkedList<File> toDelete = new LinkedList<File>();
            toDelete.push(targetDir);
            File dir;
            while ((dir = dirs.poll()) != null) {
                for (File sub : dir.listFiles()) {
                    if (sub.isDirectory()) {
                        dirs.push(sub);
                        toDelete.push(sub);
                    } else {
                        sub.delete();
                    }
                }
            }
            while ((dir = toDelete.pollLast()) != null) {
                dir.delete();
            }
        }
    }

    public static class Crc32Stream extends OutputStream {
        private CRC32 crc32;

        public Crc32Stream() {
            crc32 = new CRC32();
        }

        @Override
        public void write(int b) throws IOException {
            crc32.update(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            crc32.update(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            crc32.update(b, off, len);
        }

        public long getValue() {
            return crc32.getValue();
        }
    }
    
    public static long getCRC32 (File path) throws IOException {
        IllegalArgumentAssertion.assertNotNull(path, "path");
        IllegalStateAssertion.assertTrue(path.isFile(), "Invalid file path: " + path);

        Crc32Stream crc32Stream = new Crc32Stream();
        InputStream input = new FileInputStream(path);
        try {
            copy(input, crc32Stream);
        } finally {
            input.close();
        }
        return crc32Stream.getValue();
    } 
}
