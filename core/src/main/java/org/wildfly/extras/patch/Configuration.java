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
package org.wildfly.extras.patch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.wildfly.extras.patch.aether.AetherFactory;

public final class Configuration {

    public static final String PROPERTY_SERVER_HOME = "server.home";
    public static final String PROPERTY_REPOSITORY_URL = "repository.url";
    public static final String PROPERTY_REPOSITORY_USERNAME = "repository.username";
    public static final String PROPERTY_REPOSITORY_PASSWORD = "repository.password";
    public static final String PROPERTY_AETHER_FACTORY = "aether.factory";

    private File serverPath;
    private URL repoUrl;
    private String aetherFactory;
    private String username;
    private String password;

    // Hide ctor
    private Configuration() {
    }

    public static Configuration load(URL configURL) throws IOException {
        Properties props = new Properties();
        InputStream input = configURL.openStream();
        try {
            props.load(input);
        } finally {
            input.close();
        }
        return load(props);
    }

    public static Configuration load(Properties props) {
        Configuration config = new Configuration();
        String propval = props.getProperty(PROPERTY_SERVER_HOME);
        if (propval != null) {
            config.serverPath = new File(propval);
        }
        propval = props.getProperty(PROPERTY_REPOSITORY_URL);
        if (propval != null) {
            try {
                config.repoUrl = new URL(propval);
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        propval = props.getProperty(PROPERTY_REPOSITORY_USERNAME);
        if (propval != null) {
            config.username = propval;
        }
        propval = props.getProperty(PROPERTY_REPOSITORY_PASSWORD);
        if (propval != null) {
            config.password = propval;
        }
        propval = props.getProperty(PROPERTY_AETHER_FACTORY);
        if (propval != null) {
            config.aetherFactory = propval;
        }
        return config;
    }

    public void loadPatchToolBuilder(PatchToolBuilder builder) {
        if (repoUrl != null) {
            builder.repositoryURL(repoUrl);
        }
        if (serverPath != null) {
            builder.serverPath(serverPath);
        }
        if (username != null && password != null) {
            builder.credentials(username, password);
        }
        if (aetherFactory != null) {
            try {
                Class<?> clazz = getClass().getClassLoader().loadClass(aetherFactory);
                builder.aetherFactory((AetherFactory) clazz.newInstance());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
