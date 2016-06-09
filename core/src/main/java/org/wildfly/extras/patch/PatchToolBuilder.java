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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.locks.ReentrantLock;

import org.wildfly.extras.patch.aether.AetherFactory;
import org.wildfly.extras.patch.internal.DefaultPatchTool;
import org.wildfly.extras.patch.repository.AetherRepository;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.repository.RepositoryClient;
import org.wildfly.extras.patch.server.ServerFactory;
import org.wildfly.extras.patch.server.WildFlyServer;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

/**
 * The default {@link PatchTool} builder.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public final class PatchToolBuilder {

    private ReentrantLock lock = new ReentrantLock();
    private URL repoUrl;
    private File serverPath;
    private ServerFactory serverFactory;
    private AetherFactory aetherFactory;
    private String username;
    private String password;

    private Server server;
    private Repository repository;

    public PatchToolBuilder loadConfiguration(URL configUrl) throws IOException {
        IllegalArgumentAssertion.assertNotNull(configUrl, "configUrl");
        Configuration config = Configuration.load(configUrl);
        config.loadPatchToolBuilder(this);
        return this;
    }

    public PatchToolBuilder customLock(ReentrantLock lock) {
        this.lock = lock;
        return this;
    }

    public PatchToolBuilder serverPath(File serverPath) {
        this.serverPath = serverPath;
        return this;
    }

    public PatchToolBuilder repositoryURL(URL repoUrl) {
        IllegalArgumentAssertion.assertNotNull(repoUrl, "repoUrl");
        this.repoUrl = repoUrl;
        return this;
    }

    public PatchToolBuilder aetherFactory(AetherFactory aetherFactory) {
        IllegalArgumentAssertion.assertNotNull(aetherFactory, "aetherFactory");
        this.aetherFactory = aetherFactory;
        return this;
    }

    public PatchToolBuilder targetServer(ServerFactory serverFactory) {
        IllegalArgumentAssertion.assertNotNull(serverFactory, "serverFactory");
        this.serverFactory = serverFactory;
        return this;
    }

    public PatchToolBuilder credentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public PatchTool build() {
        return new DefaultPatchTool(lock, buildServer(), buildRepository());
    }

    private Server buildServer() {
        if (server == null) {
            if (serverFactory != null) {
                server = serverFactory.getServer();
            } else {
                if (serverPath == null) {
                    serverPath = WildFlyServer.getDefaultServerPath();
                }
                if (serverPath != null) {
                    server = new WildFlyServer(lock, serverPath);
                }
            }
        }
        return server;
    }

    private Repository buildRepository() {
        if (repository == null) {

            // Aether repository
            if (aetherFactory != null) {
                repository = new AetherRepository(lock, aetherFactory);

            } else {

                if (repoUrl == null) {
                    repoUrl = buildServer().getDefaultRepositoryURL();
                    IllegalStateAssertion.assertNotNull(repoUrl, "Cannot obtain repository URL");
                }

                // Remote jaxws repository
                String protocol = repoUrl.getProtocol();
                if (protocol.startsWith("http")) {
                    repository = new RepositoryClient(lock, repoUrl, username, password);
                }

                // Local file repository
                if (protocol.equals("file")) {
                    File rootPath = getAbsolutePath(repoUrl);
                    repository = new LocalFileRepository(lock, rootPath);
                }

                IllegalStateAssertion.assertNotNull(repository, "Unsupported protocol: " + protocol);
            }
        }
        return repository;
    }

    private File getAbsolutePath(URL url) {
        try {
            return new File(URLDecoder.decode(url.getPath(), "UTF-8")).getAbsoluteFile();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
