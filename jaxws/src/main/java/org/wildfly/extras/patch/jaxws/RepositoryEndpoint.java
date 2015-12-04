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
package org.wildfly.extras.patch.jaxws;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.activation.DataHandler;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.repository.PackageAdapter;
import org.wildfly.extras.patch.repository.RepositoryService;
import org.wildfly.extras.patch.repository.SmartPatchAdapter;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

@WebService(targetNamespace = RepositoryService.TARGET_NAMESPACE, endpointInterface = "org.wildfly.extras.patch.repository.RepositoryService")
public class RepositoryEndpoint implements RepositoryService {

	@Resource
	private WebServiceContext context;
	
    private ReentrantLock lock = new ReentrantLock();
	private Repository delegate;
	
    @PostConstruct
    public void postConstruct() {
    	delegate = new LocalFileRepository(lock, getRepositoryURL());
    }

    @Override
    public URL getBaseURL() {
    	return delegate.getBaseURL();
    }

	@Override
	public String[] queryAvailable(String prefix) {
        lock.tryLock();
        try {
            List<String> result = new ArrayList<>();
            for (PatchId pid : delegate.queryAvailable(prefix)) {
                result.add(pid.toString());
            }
            return result.toArray(new String[result.size()]);
        } finally {
            lock.unlock();
        }
	}

	@Override
	public String getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        lock.tryLock();
        try {
            PatchId patchId = delegate.getLatestAvailable(prefix);
            return patchId != null ? patchId.toString() : null;
        } finally {
            lock.unlock();
        }
	}

	@Override
	public PackageAdapter getPackage(String patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return PackageAdapter.fromPackage(delegate.getPackage(PatchId.fromString(patchId)));
        } finally {
            lock.unlock();
        }
	}

	@Override
	public String addArchive(String patchId, DataHandler dataHandler, String oneoffSpec, String[] deps, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(dataHandler, "dataHandler");
        lock.tryLock();
        try {
            PatchId oneoffId = oneoffSpec != null ? PatchId.fromString(oneoffSpec) : null;
            Set<PatchId> dependencies = new HashSet<>();
            if (deps != null) {
                for (String pidSpec : deps) {
                    dependencies.add(PatchId.fromString(pidSpec));
                }
            }
            return delegate.addArchive(PatchId.fromString(patchId), dataHandler, oneoffId, dependencies, force).toString();
        } finally {
            lock.unlock();
        }
	}

	@Override
    public boolean removeArchive(String patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return delegate.removeArchive(PatchId.fromString(patchId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addPostCommand(String patchId, String[] cmdarr) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(cmdarr, "cmdarr");
        lock.tryLock();
        try {
            delegate.addPostCommand(PatchId.fromString(patchId), cmdarr);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SmartPatchAdapter getSmartPatch(PackageAdapter seedPatch, String patchId) {
        lock.tryLock();
        try {
            Package seed = seedPatch != null ? seedPatch.toPackage() : null;
            PatchId pid = patchId != null ? PatchId.fromString(patchId) : null;
            SmartPatch smartPatch = delegate.getSmartPatch(seed, pid);
            return SmartPatchAdapter.fromSmartPatch(smartPatch);
        } finally {
            lock.unlock();
        }
    }

    private URL getRepositoryURL() {
        URL repoUrl = LocalFileRepository.getDefaultRepositoryURL();
		ServletContext servletContext = (ServletContext) context.getMessageContext().get(MessageContext.SERVLET_CONTEXT);
    	String repoSpec = servletContext.getInitParameter(Repository.SYSTEM_PROPERTY_REPOSITORY_URL);
        if (repoSpec != null) {
            try {
            	repoUrl = new URL(repoSpec);
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return repoUrl;
	}
}
