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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Abstract installer for installing/uninstalling add on distros on an
 * EAP instance.
 */
public abstract class AbstractInstaller {

    protected Path eapHome;
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
            eapHome = Paths.get(args.removeFirst());
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

    private void run() throws Exception {
        if (eapHome == null) {
            eapHome = new File(".").toPath().toAbsolutePath();
        }
        
        validateHomePath(eapHome);
        
        ClassLoader classLoader = getClass().getClassLoader();
        
        // Install Fuse Patch Tool
        Path fusepatchPath = eapHome.resolve(Paths.get("modules/system/layers/fuse/org/wildfly/extras/patch"));
        if (!fusepatchPath.toFile().exists()) {
            
            String resname = "META-INF/fuse-patch.version";
            InputStream resource = classLoader.getResourceAsStream(resname);
            IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);
            BufferedReader br = new BufferedReader(new InputStreamReader(resource));
            String fusepatchVersion = br.readLine().trim();
            
            resname = "META-INF/repository/fuse-patch-distro-wildfly-" + fusepatchVersion + ".zip";
            resource = classLoader.getResourceAsStream(resname);
            IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);

            Properties installedFiles = new Properties();
            try (ZipInputStream distro = new ZipInputStream(resource)) {
                unpack(resname, distro, installedFiles);
            }
        } 
        
        // Copy repository content
        Path jarPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        try (ZipInputStream zipstream = new ZipInputStream(new FileInputStream(jarPath.toFile()))) {
            Path repoPath = eapHome.resolve(Paths.get("fusepatch/repository"));
            IllegalStateAssertion.assertTrue(repoPath.toFile().isDirectory(), "Not a valid repository path: " + repoPath);
            for (ZipEntry entry = zipstream.getNextEntry(); entry != null; entry = zipstream.getNextEntry()) {
                String name = entry.getName();
                if (name.startsWith("META-INF/repository") && !entry.isDirectory()) {
                    Path path = Paths.get(name);
                    Path targetPath = repoPath.resolve(path.getFileName());
                    if (targetPath.toFile().exists()) {
                        warn("Skip already existing patch file: " + path.getFileName());
                    } else {
                        warn("Copy to repository: " + path.getFileName());
                        Files.copy(zipstream, targetPath);
                    }
                }
            }
        }
        
        // Run the install commands 
        String resname = "META-INF/fuse-install.commands";
        InputStream resource = classLoader.getResourceAsStream(resname);
        IllegalStateAssertion.assertNotNull(resource, "Cannot obtain resource: " + resname);
        BufferedReader br = new BufferedReader(new InputStreamReader(resource));
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
                info("Run command: " + line);
                Process proc = Support.exec(line.split("\\s"), eapHome.toFile());
                IllegalStateAssertion.assertEquals(0, proc.waitFor(), "Command did not terminate normally");
            }
        }
    }

    private void validateHomePath(Path homePath) {
        Path versionPath = homePath.resolve(Paths.get("modules/system/layers/base/org/jboss/as/version/main/module.xml"));
        IllegalStateAssertion.assertTrue(versionPath.toFile().isFile(), "The path '" + eapHome + "' is not a valid EAP installation location.");
    }

    private void unpack(String resname, ZipInputStream zipstream, Properties installedFiles) throws IOException {

        info("Installing " + Paths.get(resname).getFileName());
        for (ZipEntry entry = zipstream.getNextEntry(); entry != null; entry = zipstream.getNextEntry()) {
            String entryName = entry.getName();
            Path targetPath = eapHome.resolve(entryName);
            if (entry.isDirectory()) {
                targetPath.toFile().mkdirs();
            } else {
                if (targetPath.toFile().exists()) {
                    if (Support.computeCRC32(targetPath.toFile()) != entry.getCrc()) {
                        info("WARN: Existing file found: not overwriting: " + targetPath);
                    }
                } else {
                    debug("extracting: " + entryName);
                    Files.copy(zipstream, targetPath);
                    if (targetPath.toString().endsWith(".sh") || targetPath.toString().endsWith(".bat")) {
                        targetPath.toFile().setExecutable(true);
                    }
                    installedFiles.setProperty(entry.getName(), "" + entry.getCrc());
                }
            }
        }
    }
}
