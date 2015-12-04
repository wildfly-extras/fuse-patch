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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

import javax.xml.namespace.QName;

import org.wildfly.extras.patch.internal.DefaultPatchTool;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;


/**
 * The default {@link PatchTool} builder.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public final class PatchToolBuilder {

    private Path serverPath;
    private URL repoUrl;
    private QName serviceName;

    public PatchToolBuilder serverPath(Path serverPath) {
        this.serverPath = serverPath;
        return this;
    }

    public PatchToolBuilder repositoryPath(Path repoPath) {
        IllegalArgumentAssertion.assertNotNull(repoPath, "repoPath");
        try {
            this.repoUrl = repoPath.toFile().toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
        return this;
    }

    public PatchToolBuilder localRepository(URL repoUrl) {
        IllegalArgumentAssertion.assertNotNull(repoUrl, "repoUrl");
        this.repoUrl = repoUrl;
        return this;
    }

    public PatchToolBuilder jaxwsRepository(QName serviceName, URL wsdlUrl) {
        IllegalArgumentAssertion.assertNotNull(serviceName, "serviceName");
        IllegalArgumentAssertion.assertNotNull(wsdlUrl, "wsdlUrl");
        this.serviceName = serviceName;
        this.repoUrl = wsdlUrl;
        return this;
    }

    public PatchTool build() {
        return new DefaultPatchTool(serverPath, serviceName, repoUrl);
    }
}
