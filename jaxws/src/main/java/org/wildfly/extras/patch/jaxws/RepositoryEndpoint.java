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
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.activation.DataHandler;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.repository.PatchAdapter;
import org.wildfly.extras.patch.repository.PatchMetadataAdapter;
import org.wildfly.extras.patch.repository.RepositoryService;
import org.wildfly.extras.patch.repository.SmartPatchAdapter;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

@WebService(targetNamespace = RepositoryService.TARGET_NAMESPACE, endpointInterface = "org.wildfly.extras.patch.repository.RepositoryService")
public class RepositoryEndpoint implements RepositoryService {

	@Resource
	private WebServiceContext context;
	
    private final ReentrantLock lock = new ReentrantLock();
    
	private Repository delegate;
	
    @PostConstruct
    public void postConstruct() {
        URL repoURL = getRepositoryURL();
        PatchToolBuilder builder = new PatchToolBuilder().customLock(lock).repositoryURL(repoURL);
        delegate = builder.build().getRepository();
    }

	@Override
	public String[] queryAvailable(String prefix) {
        lock.tryLock();
        try {
            List<String> result = new ArrayList<String>();
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
	public PatchAdapter getPatch(String patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return PatchAdapter.fromPatch(delegate.getPatch(PatchId.fromString(patchId)));
        } finally {
            lock.unlock();
        }
	}

	@Override
	public String addArchive(PatchMetadataAdapter metadata, DataHandler dataHandler, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        IllegalArgumentAssertion.assertNotNull(dataHandler, "dataHandler");
        lock.tryLock();
        try {
            return delegate.addArchive(metadata.toPatchMetadata(), dataHandler, force).toString();
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
    public SmartPatchAdapter getSmartPatch(PatchAdapter seedPatch, String patchId) {
        lock.tryLock();
        try {
            Patch seed = seedPatch != null ? seedPatch.toPatch() : null;
            PatchId pid = patchId != null ? PatchId.fromString(patchId) : null;
            
            // Derive the target patch id from the seed patch id
            if (pid == null) {
                IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
                PatchMetadata metadata = seedPatch.getMetadata().toPatchMetadata();
                pid = delegate.getLatestAvailable(metadata.getPatchId().getName());
            }
            
            // Assert user has required roles
            PatchMetadata metadata = delegate.getPatch(pid).getMetadata();
            HttpServletRequest servletRequest = (HttpServletRequest) context.getMessageContext().get(MessageContext.SERVLET_REQUEST);
            for (String role : metadata.getRoles()) {
                if (!servletRequest.isUserInRole(role)) {
                    throw new WebServiceException(new SecurityException("User does not have required role: " + role));
                }
            }
            
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
