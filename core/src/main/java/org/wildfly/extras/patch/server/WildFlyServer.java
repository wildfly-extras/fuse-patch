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
package org.wildfly.extras.patch.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.ManagedPaths;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

public final class WildFlyServer extends AbstractServer {

    private static final Logger LOG = LoggerFactory.getLogger(WildFlyServer.class);

    public static final String MODULE_LAYER = "fuse";

    public WildFlyServer(Lock lock, Path homePath) {
        super(lock, assertHomePath(homePath));
    }

    private static Path assertHomePath(Path homePath) {
        if (homePath == null) {
            homePath = getDefaultServerPath();
        }
        IllegalStateAssertion.assertNotNull(homePath, "Cannot obtain JBOSS_HOME");
        IllegalStateAssertion.assertTrue(homePath.toFile().isDirectory(), "Directory JBOSS_HOME does not exist: " + homePath);
        return homePath;
    }

    public static Path getDefaultServerPath() {
        String jbossHome = System.getProperty("jboss.home");
        if (jbossHome == null) {
            jbossHome = System.getProperty("jboss.home.dir");
        }
        if (jbossHome == null) {
            jbossHome = System.getenv("JBOSS_HOME");
        }
        if (jbossHome == null) {
            Path currpath = Paths.get(".");
            if (currpath.resolve("jboss-modules.jar").toFile().exists()) {
                jbossHome = currpath.toAbsolutePath().toString();
            }
        }
        return jbossHome != null ? Paths.get(jbossHome) : null;
    }

    @Override
    public URL getDefaultRepositoryURL() {
        Path jbossHome = getServerHome().toFile().toPath();
        Path repoPath = jbossHome.resolve("fusepatch").resolve("repository");
        try {
            return repoPath.toUri().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid repository path", ex);
        }
    }

    @Override
    protected void updateServerFiles(SmartPatch smartPatch, ManagedPaths managedPaths) throws IOException {
        super.updateServerFiles(smartPatch, managedPaths);

        Path homePath = getServerHome();
        
        // Ensure Fuse layer exists
        Path modulesPath = homePath.resolve("modules");
        if (modulesPath.toFile().isDirectory()) {
            Properties props = new Properties();
            Path layersPath = modulesPath.resolve("layers.conf");
            if (layersPath.toFile().isFile()) {
                try (FileReader fr = new FileReader(layersPath.toFile())) {
                    props.load(fr);
                }
            }
            List<String> layers = new ArrayList<>();
            String value = props.getProperty("layers");
            if (value != null) {
                for (String layer : value.split(",")) {
                    layers.add(layer.trim());
                }
            }
            if (!layers.contains(MODULE_LAYER)) {
                layers.add(0, MODULE_LAYER);
                value = "";
                for (String layer : layers) {
                    value += "," + layer;
                }
                value = value.substring(1);
                props.setProperty("layers", value);
                LOG.warn("Layers config does not contain '" + MODULE_LAYER + "', writing: {}", value);
                try (FileWriter fw = new FileWriter(layersPath.toFile())) {
                    props.store(fw, "Fixed by fusepatch");
                }
            }
        }
    }

    @Override
    public void cleanUp() {
        final List<String> currentFiles = new ArrayList<>();
        Path patchModuleBase = getServerHome().resolve("modules/system/layers/fuse/org/wildfly/extras/");
        if (patchModuleBase.toFile().exists()) {
            String[] patchModuleNames = {"config", "patch"};
            for (String moduleName : patchModuleNames) {
                Path modulePath = Paths.get(patchModuleBase.toString(), moduleName, "main");
                File moduleXML = modulePath.resolve("module.xml").toFile();
                if (moduleXML.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(moduleXML))) {
                        String line;
                        while((line = br.readLine()) != null) {
                            Pattern compile = Pattern.compile(".*path=\"(.*)\".*");
                            Matcher matcher = compile.matcher(line);
                            if (matcher.matches() && matcher.group(1).endsWith(".jar")) {
                                currentFiles.add(matcher.group(1));
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Clean up operation failed reading {}", moduleXML.getAbsoluteFile());
                    }
                }

                String[] filesToClean = modulePath.toFile().list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return !currentFiles.contains(name) && name.endsWith(".jar");
                    }
                });

                for (String fileName : filesToClean) {
                    Path path = modulePath.resolve(fileName);
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOG.warn("Clean up operation failed to delete {}", path.toString());
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return "WildFlyServer[home=" + getServerHome() + "]";
    }
}
