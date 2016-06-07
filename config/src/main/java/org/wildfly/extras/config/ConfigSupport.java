/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extras.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.wildfly.extras.config.LayerConfig.Type;
import org.wildfly.extras.config.internal.IllegalStateAssertion;

public class ConfigSupport {

    public static ConfigContext createContext(File jbossHome, File configuration, Document doc) {
        return new ConfigContext(jbossHome, configuration, doc);
    }

    public static void applyConfigChange(File jbossHome, List<String> configs, boolean enable) throws Exception {

        IllegalStateAssertion.assertFalse(configs.isEmpty(), "No configurations specified");

        Iterator<ConfigPlugin> itsrv;
        ClassLoader classLoader = ConfigSupport.class.getClassLoader();

        // In wildfly every plugin lives in its own module.
        // The module identity corresponds to the config name
        if (classLoader instanceof ModuleClassLoader) {
            List<ConfigPlugin> plugins = new ArrayList<ConfigPlugin>();
            for (String config : configs) {
                ModuleLoader moduleLoader = Module.getCallerModuleLoader();
                ModuleIdentifier modid = ModuleIdentifier.create("org.wildfly.extras.config.plugin." + config);
                ModuleClassLoader modcl = moduleLoader.loadModule(modid).getClassLoader();
                Iterator<ConfigPlugin> auxit = ServiceLoader.load(ConfigPlugin.class, modcl).iterator();
                while (auxit.hasNext()) {
                    plugins.add(auxit.next());
                }
            }
            itsrv = plugins.iterator();
        } else {
            itsrv = ServiceLoader.load(ConfigPlugin.class, classLoader).iterator();
        }

        while (itsrv.hasNext()) {
            ConfigPlugin plugin = itsrv.next();
            if (configs.contains(plugin.getConfigName())) {

                ConfigLogger.info("Processing config for: " + plugin.getConfigName());

                applyLayerChanges(jbossHome, plugin, enable);
                applyConfigurationChanges(jbossHome, plugin, enable);
            }
        }
    }

    public static void applyLayerChanges(File jbossHome, ConfigPlugin plugin, boolean enable) throws IOException {

        File layersFile = new File("modules" + File.separator + "layers.conf");
        Properties layersProperties = new Properties();
        List<String> layers = new ArrayList<String>();
        if (layersFile.exists()) {
            FileInputStream is = new FileInputStream(layersFile);
            try {
                layersProperties.load(is);
            } finally {
                is.close();
            }
            String layersValue = layersProperties.getProperty("layers");
            if (layersValue == null) {
                layersValue = "";
            }
            for (String s : layersValue.split(",")) {
                s = s.trim();
                if (s.length() > 0) {
                    layers.add(s);
                }
            }
        }

        layers = applyLayerChanges(plugin, layers, enable);

        String layersValue = "";
        for (String layer : layers) {
            if (layersValue.length() != 0) {
                layersValue += ",";
            }
            layersValue += layer;
        }

        layersProperties.put("layers", layersValue);
        ConfigLogger.info("\tWriting 'layers=" + layersValue + "' to: " + layersFile);
        FileOutputStream out = new FileOutputStream(layersFile);
        try {
            layersProperties.store(out, null);
        } finally {
            out.close();
        }
    }

    public static List<String> applyLayerChanges(ConfigPlugin plugin, List<String> layers, boolean enable) throws IOException {

        class LayerData implements Comparable<LayerData> {
            String name;
            int primaryPriority;
            int secondaryPriority;
            LayerConfig config;

            @Override
            public int compareTo(LayerData o) {
                if (o.primaryPriority != primaryPriority) {
                    return primaryPriority - o.primaryPriority;
                } else {
                    return secondaryPriority - o.secondaryPriority;
                }
            }
        }

        List<LayerData> workingList = new ArrayList<LayerData>();
        ArrayList<LayerConfig> configs = new ArrayList<LayerConfig>(plugin.getLayerConfigs());

        // Lets match up existing layers to the layer config..
        int secondaryCounter = 0;
        for (int i = 0; i < layers.size(); i++) {

            String name = layers.get(i);
            LayerData ld = new LayerData();
            ld.name = name;
            ld.primaryPriority = 0;
            ld.secondaryPriority = i;

            // Now lets see if we can match the layer to a LayerConfig...
            boolean add = true;
            for (LayerConfig config : configs) {
                if (config.pattern.matcher(name).matches()) {
                    if (enable) {
                        ld.config = config;
                        if (config.type == Type.INSTALLING) {
                            ld.name = config.name;
                        }
                        ld.primaryPriority = config.priority;
                        ld.secondaryPriority = secondaryCounter++;
                    } else {
                        if (config.type == Type.INSTALLING) {
                            add = false;
                        }
                    }
                    break;
                }
            }
            if (add) {
                workingList.add(ld);
            }
        }

        // Lets find out which layers did not match..
        for (LayerData layerData : workingList) {
            if (layerData.config != null) {
                configs.remove(layerData.config);
            }
        }

        // Lets deal with the layers which have not been configured yet.
        for (LayerConfig config : configs) {
            switch (config.type) {
                case OPTIONAL:
                    // We wont add it.. if it was available,
                    // we would have already applied a priority to it.
                    break;

                case REQUIRED:
                    if (enable) {
                        throw new ConfigException("Required layer has not yet been configured: " + config.name);
                    }
                    break;
                case INSTALLING:
                    // This is a layer we are installing.. so lets add it in.
                    if (enable) {
                        LayerData ld = new LayerData();
                        ld.name = config.name;
                        ld.config = config;
                        ld.primaryPriority = config.priority;
                        ld.secondaryPriority = secondaryCounter++;
                        workingList.add(ld);
                    }
                    break;
            }

        }

        // Now lets sort the layers by primary and secondary priority.
        Collections.sort(workingList);

        List<String> result = new ArrayList<String>();
        for (LayerData layerData : workingList) {
            result.add(layerData.name);
        }

        // We cannot remove this layer
        // otherwise the config tool cannot be accessed any more
        if (result.isEmpty()) {
            result.add(LayerConfig.FUSE_LAYER.name);
        }

        return Collections.unmodifiableList(result);
    }

    private static void applyConfigurationChanges(File jbossHome, ConfigPlugin plugin, boolean enable) throws Exception {

        List<File> standalonePaths = new ArrayList<File>();
        standalonePaths.add(new File("standalone" + File.separator + "configuration" + File.separator + "standalone.xml"));
        standalonePaths.add(new File("standalone" + File.separator + "configuration" + File.separator + "standalone-full.xml"));
        standalonePaths.add(new File("standalone" + File.separator + "configuration" + File.separator + "standalone-full-ha.xml"));
        standalonePaths.add(new File("standalone" + File.separator + "configuration" + File.separator + "standalone-ha.xml"));

        List<File> domainPaths = new ArrayList<File>();
        domainPaths.add(new File("domain" + File.separator + "configuration" + File.separator + "domain.xml"));

        String message = (enable ? "\tEnable " : "\tDisable ") + plugin.getConfigName() + " configuration in: ";
        String lineSeparator = System.getProperty("line.separator");

        SAXBuilder jdom = new SAXBuilder();
        for (File p : standalonePaths) {
            File path = new File(jbossHome, p.getPath());
            if (path.isFile()) {
                ConfigLogger.info(message + path);
                Document doc = jdom.build(path.toURI().toURL());

                ConfigContext context = new ConfigContext(jbossHome, path, doc);
                plugin.applyStandaloneConfigChange(context, enable);

                XMLOutputter output = new XMLOutputter();
                output.setFormat(Format.getRawFormat().setLineSeparator(lineSeparator));
                String newXML = output.outputString(doc);
                backup(path);
                writeFile(path, newXML, "UTF-8");
            }
        }

        for (File p : domainPaths) {
            File path = new File(jbossHome, p.getPath());
            if (path.isFile()) {
                ConfigLogger.info(message + path);
                Document doc = jdom.build(path.toURI().toURL());

                ConfigContext context = new ConfigContext(jbossHome, path, doc);
                plugin.applyDomainConfigChange(context, enable);

                XMLOutputter output = new XMLOutputter();
                output.setFormat(Format.getRawFormat().setLineSeparator(lineSeparator));
                String newXML = output.outputString(doc);
                backup(path);
                writeFile(path, newXML, "UTF-8");
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        FileChannel input = new FileInputStream(source).getChannel();
        try {
            FileChannel output = new FileOutputStream(destination).getChannel();
            try {
                output.transferFrom(input, 0, input.size());
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private static void backup(File path) throws IOException {
        String name = path + ".bak";
        int counter = 2;
        File bak = new File(name);
        while (bak.exists()) {
            name = path + ".bak" + counter;
            counter++;
            bak = new File(name);
        }
        copyFile(path, bak);
    }

    private static File writeFile(File path, String value, String encoding) throws IOException {
        byte[] bytes = value.getBytes(encoding);
        FileOutputStream output = new FileOutputStream(path);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
        return path;
    }

    public static File getJBossHome() throws UnsupportedEncodingException {

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

        File jbossHomeFile = new File(jbossHome);
        if (!jbossHomeFile.isDirectory())
            throw new ConfigException("Cannot obtain JBOSS_HOME: " + jbossHome);

        File standalonePath = new File(jbossHomeFile, "standalone" + File.separator + "configuration");
        if (!standalonePath.exists())
            throw new ConfigException("Path to standalone configutration does not exist: " + standalonePath);

        File domainPath = new File(jbossHomeFile, "domain" + File.separator + "configuration");
        if (!domainPath.exists())
            throw new ConfigException("Path to domain configutration does not exist: " + domainPath);

        return jbossHomeFile;
    }

    public static Element createElementFromText(String xml) {
        SAXBuilder jdom = new SAXBuilder();
        Document doc;
        try {
            doc = jdom.build(new StringReader(xml));
        } catch (JDOMException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return (Element) doc.getRootElement().clone();
    }

    public static Element loadElementFrom(URL resource) {
        try {
            byte[] data = loadBytesFromURL(resource);
            String xml = new String(data, "UTF-8");
            return createElementFromText(xml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] loadBytesFromURL(URL resource) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[10234];
        InputStream in = resource.openStream();
        try {
            while (true) {
                int count = in.read(buffer, 0, buffer.length);
                if (count < 0)
                    break;
                out.write(buffer, 0, count);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    @Deprecated
    public static Element findElementWithAttributeValue(Element element, String name, Namespace ns, String attrName, String attrValue) {
        return findElementWithAttributeValue(element, name, attrName, attrValue, ns);
    }

    @SuppressWarnings("unchecked")
    public static Element findElementWithAttributeValue(Element element, String name, String attrName, String attrValue, Namespace... supportedNamespaces) {
        for (Namespace ns : supportedNamespaces) {
            if (element.getName().equals(name) && element.getNamespace().equals(ns)) {
                Attribute attribute = element.getAttribute(attrName);
                if (attribute != null && attrValue.equals(attribute.getValue())) {
                    return element;
                }
            }
            for (Element ch : (List<Element>) element.getChildren()) {
                Element result = findElementWithAttributeValue(ch, name, attrName, attrValue, supportedNamespaces);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static Element findChildElement(Element parent, String name, Namespace... supportedNamespaces) {
        for (Namespace ns : supportedNamespaces) {
            Element result = parent.getChild(name, ns);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static void assertExists(Element extensions, String message) {
        if (extensions == null) {
            throw new ConfigException(message);
        }
    }

    public static Map<String, Element> mapByAttributeName(List<Element> elements, String attrName) {
        LinkedHashMap<String, Element> rc = new LinkedHashMap<String, Element>();
        for (Element element : elements) {
            Attribute attribute = element.getAttribute(attrName);
            if (attribute != null) {
                rc.put(attribute.getValue(), element);
            }
        }
        return rc;
    }

    @Deprecated
    public static List<Element> findProfileElements(Document doc, Namespace ns) {
        return findProfileElements(doc, new Namespace[] {ns});
    }

    @SuppressWarnings("unchecked")
    public static List<Element> findProfileElements(Document doc, Namespace... supportedNamespaces) {
        List<Element> result = new ArrayList<Element>();
        for (Namespace ns : supportedNamespaces) {
            Element profile = doc.getRootElement().getChild("profile", ns);
            if (profile != null) {
                result.add(profile);
            }
            Element profiles = doc.getRootElement().getChild("profiles", ns);
            if (profiles != null) {
                result.addAll(profiles.getChildren("profile", ns));
            }
        }
        return result;
    }
}
