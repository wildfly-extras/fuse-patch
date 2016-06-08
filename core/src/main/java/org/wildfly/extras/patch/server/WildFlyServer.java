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

    public WildFlyServer(Lock lock, File homePath) {
        super(lock, assertHomePath(homePath));
    }

    private static File assertHomePath(File homePath) {
        if (homePath == null) {
            homePath = getDefaultServerPath();
        }
        IllegalStateAssertion.assertNotNull(homePath, "Cannot obtain JBOSS_HOME");
        IllegalStateAssertion.assertTrue(homePath.isDirectory(), "Directory JBOSS_HOME does not exist: " + homePath);
        return homePath;
    }

    public static File getDefaultServerPath() {
        String jbossHome = System.getProperty("jboss.home");
        if (jbossHome == null) {
            jbossHome = System.getProperty("jboss.home.dir");
        }
        if (jbossHome == null) {
            jbossHome = System.getenv("JBOSS_HOME");
        }
        if (jbossHome == null) {
            File currpath = new File(".");
            if (new File(currpath, "jboss-modules.jar").exists()) {
                jbossHome = currpath.getAbsolutePath();
            }
        }
        return jbossHome != null ? new File(jbossHome) : null;
    }

    @Override
    public URL getDefaultRepositoryURL() {
        File jbossHome = getServerHome();
        File repoPath = new File(jbossHome, "fusepatch" + File.separator + "repository");
        try {
            return repoPath.toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid repository path", ex);
        }
    }

    @Override
    protected void updateServerFiles(SmartPatch smartPatch, ManagedPaths managedPaths) throws IOException {
        super.updateServerFiles(smartPatch, managedPaths);

        File homePath = getServerHome();
        
        // Ensure Fuse layer exists
        File modulesPath = new File(homePath, "modules");
        if (modulesPath.isDirectory()) {
            Properties props = new Properties();
            File layersPath = new File(modulesPath, "layers.conf");
            if (layersPath.isFile()) {
                FileReader fr = new FileReader(layersPath);
                try {
                    props.load(fr);
                } finally {
                    fr.close();
                }
            }
            List<String> layers = new ArrayList<String>();
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
                FileWriter fw = new FileWriter(layersPath);
                try {
                    props.store(fw, "Fixed by fusepatch");
                } finally {
                    fw.close();
                }
            }
        }
    }

    @Override
    public void cleanUp() {
        final List<String> currentFiles = new ArrayList<String>();
        File patchModuleBase = new File(getServerHome(),
                "modules" + File.separator +
                "system" + File.separator +
                "layers" + File.separator +
                "fuse" + File.separator +
                "org" + File.separator +
                "wildfly" + File.separator +
                "extras");
        if (patchModuleBase.exists()) {
            String[] patchModuleNames = {"config", "patch"};
            for (String moduleName : patchModuleNames) {
                File modulePath = new File(patchModuleBase, moduleName + File.separator + "main");
                File moduleXML = new File(modulePath, "module.xml");
                if (moduleXML.exists()) {
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new FileReader(moduleXML));
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
                    } finally {
                        try {
                            if (br != null)
                                br.close();
                        } catch (IOException e) {
                        }
                    }
                }

                String[] filesToClean = modulePath.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return !currentFiles.contains(name) && name.endsWith(".jar");
                    }
                });

                for (String fileName : filesToClean) {
                    File path = new File(modulePath, fileName);
                    if (!path.delete()) {
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
