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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Abstract installer for installing/uninstalling add on distros on an
 * EAP instance.
 */
public abstract class AbstractInstaller {

    protected File eapHome;
    protected boolean verbose;

    abstract public String getJarName();

    public void main(LinkedList<String> args) {
        while (!args.isEmpty()) {
            String arg = args.peekFirst();
            if ("--help".equals(arg)) {
                args.removeFirst();
                printHelp();
                System.exit(0);
            } else if ("--verbose".equals(arg)) {
                args.removeFirst();
                verbose = true;
            } else {
                break;
            }
        }

        if (!args.isEmpty()) {
            eapHome = new File(args.removeFirst());
        }

        try {
            run();
            System.exit(0);
        } catch (Throwable th) {
            if (verbose) {
                th.printStackTrace(System.err);
            } else {
                String message = th.getMessage();
                if (message != null) {
                    error("Error: " + message);
                } else {
                    error("Unexpected Error: " + th);
                }
            }
            System.exit(1);
        }
    }

    protected void printHelp() {
        error("NAME\n" +
                "        " + getJarName() + " - Installs the distribution\n" +
                "\n" +
                "SYNOPSIS\n" +
                "        " + getJarName() + " [options] [<eap-home>]\n" +
                "\n" +
                "OPTIONS\n" +
                "        --help\n" +
                "            Shows this help screen.\n" +
                "        --verbose\n" +
                "            Show more detailed logging.\n" +
                "\n" +
                "ARGUMENTS\n" +
                "        <eap-home>\n" +
                "            The directory of the EAP installation. If not specified, the current\n" +
                "            working directory is used\n");
    }

    protected void error(String message) {
        System.err.println(message);
    }

    protected void warn(String message) {
        System.out.println(message);
    }

    protected void info(String message) {
        System.out.println(message);
    }

    protected void debug(String message) {
        if (verbose) {
            info(message);
        }
    }

    private static void copyToFile(InputStream input, File output) throws IOException {
        OutputStream outputStream = new FileOutputStream(output);
        try {
            byte[] bytes = new byte[4096];
            int read = input.read(bytes);
            while (read > 0) {
                outputStream.write(bytes, 0, read);
                read = input.read(bytes);
            }
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    private void run() throws Exception {
        if (eapHome == null) {
            eapHome = new File(".").getAbsoluteFile();
        }

        validateHomePath(eapHome);

        ClassLoader classLoader = getClass().getClassLoader();

        // Install Fuse Patch Tool
        File fusePatchPath = new File(eapHome, "modules/system/layers/fuse/org/wildfly/extras/patch");
        if (mustInstallPatchDistro(fusePatchPath)) {

            Version fusepatchVersion = getVersion();
            String resname = "META-INF/repository/fuse-patch-distro-wildfly-" + fusepatchVersion + ".zip";
            InputStream resource = classLoader.getResourceAsStream(resname);;
            IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);

            Properties installedFiles = new Properties();
            ZipInputStream distro = new ZipInputStream(resource);
            try {
                unpack(resname, distro, installedFiles);
            } finally {
                distro.close();
            }
        }

        // Copy repository content
        File jarPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        ZipInputStream zipstream = new ZipInputStream(new FileInputStream(jarPath));
        try {
            File repoPath = new File(eapHome, "fusepatch/repository");
            IllegalStateAssertion.assertTrue(repoPath.isDirectory(), "Not a valid repository path: " + repoPath);
            for (ZipEntry entry = zipstream.getNextEntry(); entry != null; entry = zipstream.getNextEntry()) {
                String name = entry.getName();
                if (name.startsWith("META-INF/repository") && !entry.isDirectory()) {
                    File path = new File(name);
                    File targetPath = new File(repoPath, path.getName());
                    if (targetPath.exists()) {
                        warn("Skip already existing patch file: " + path.getName());
                    } else {
                        warn("Copy to repository: " + path.getName());
                        copyToFile(zipstream, targetPath);
                    }
                }
            }
        } finally {
            zipstream.close();
        }

        // Run the install commands
        String resname = "META-INF/fuse-install.commands";
        InputStream resource = classLoader.getResourceAsStream(resname);
        IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);
        BufferedReader br = new BufferedReader(new InputStreamReader(resource));
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
                runCommand(line);
            }
        }
    }

    protected boolean mustInstallPatchDistro(File fusePatchPath) {
        return !fusePatchPath.exists();
    }

    protected final Version getVersion() {
        String resname = "META-INF/fuse-patch.version";
        InputStream resource = Support.class.getClassLoader().getResourceAsStream(resname);
        IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);
        BufferedReader br = new BufferedReader(new InputStreamReader(resource));
        try {
            return Version.get(br.readLine().trim());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read version metadata", e);
        }
    }

    protected void runCommand(String cmd) throws Exception {
        info("Run command: " + cmd);
        Process proc = Support.exec(cmd.split("\\s"), eapHome);
        IllegalStateAssertion.assertEquals(0, proc.waitFor(), "Command did not terminate normally");
    }

    private void validateHomePath(File homePath) {
        File versionPath = new File(homePath, "modules/system/layers/base/org/jboss/as/version/main/module.xml");
        IllegalStateAssertion.assertTrue(versionPath.isFile(), "The path '" + eapHome + "' is not a valid EAP installation location.");
    }

    private void unpack(String resname, ZipInputStream zipstream, Properties installedFiles) throws IOException {

        info("Installing " + new File(resname).getName());
        for (ZipEntry entry = zipstream.getNextEntry(); entry != null; entry = zipstream.getNextEntry()) {
            String entryName = entry.getName();
            File targetPath = new File(eapHome, entryName);
            if (entry.isDirectory()) {
                targetPath.mkdirs();
            } else {
                if (targetPath.exists()) {
                    if (Support.computeCRC32(targetPath) != entry.getCrc()) {
                        info("WARN: Existing file found: " + targetPath);
                    }
                }

                debug("extracting: " + entryName);
                copyToFile(zipstream, targetPath);
                if (targetPath.toString().endsWith(".sh") || targetPath.toString().endsWith(".bat")) {
                    targetPath.setExecutable(true);
                }
                installedFiles.setProperty(entry.getName(), "" + entry.getCrc());
            }
        }
    }
}
