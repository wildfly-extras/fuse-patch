/*
 * #%L
 * Fuse EAP :: Installer
 * %%
 * Copyright (C) 2015 RedHat
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

package org.wildfly.extras.patch.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

final class Support {

    static String getJavaFile() throws IOException {
        String home = System.getProperty("java.home");
        if (home != null) {
            File file = new File(home, "bin/java");
            if (file.exists() && file.canExecute()) {
                return file.getCanonicalPath();
            }
        }
        return "java";
    }

    static void pump(InputStream distro, OutputStream fos) throws IOException {
        int len;
        byte[] buffer = new byte[1024 * 4];
        while ((len = distro.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
    }

    static Thread startPump(final InputStream is, final OutputStream os) {
        Thread thread = new Thread("io") {
            @Override
            public void run() {
                try {
                    pump(is, os);
                } catch (IOException e) {
                }
            }
        };
        thread.start();
        return thread;
    }

    static long computeCRC32(File file) throws IOException {
        CRC32 crc32 = new CRC32();
        FileInputStream is = new FileInputStream(file);
        try {
            int len;
            byte[] buffer = new byte[1024 * 4];
            while ((len = is.read(buffer)) > 0) {
                crc32.update(buffer, 0, len);
            }
        } finally {
            is.close();
        }
        return crc32.getValue();
    }

    static Process exec(String[] args, File dir) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(args, null, dir);
        startPump(process.getInputStream(), System.out);
        startPump(process.getErrorStream(), System.err);
        return process;
    }
}
