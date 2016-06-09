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
package org.wildfly.extras.patch.repository;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

public final class RepositoryClient implements Repository {

    private final Lock lock;
    private final URL endpointUrl;
    private final RepositoryService delegate;

    public RepositoryClient(Lock lock, URL endpointUrl, String username, String password) {
        IllegalArgumentAssertion.assertNotNull(endpointUrl, "endpointUrl");
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        this.endpointUrl = endpointUrl;
        this.lock = lock;

        URL wsdlUrl = getClass().getClassLoader().getResource("/jaxws/repository-endpoint.wsdl");
        this.delegate = Service.create(wsdlUrl, RepositoryService.SERVICE_QNAME).getPort(RepositoryService.class);

        if (username != null && password != null) {
            BindingProvider bp = (BindingProvider) delegate;
            bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointUrl.toString());
            bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, username);
            bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
        }
    }

    @Override
    public URL getRepositoryURL() {
        return endpointUrl;
    }

    @Override
    public List<PatchId> queryAvailable(String prefix) {
        lock.tryLock();
        try {
            List<PatchId> result = new ArrayList<PatchId>();
            String[] available = delegate.queryAvailable(prefix);
            if (available != null) {
                for (String spec : available) {
                    result.add(PatchId.fromString(spec));
                }
            }
            return Collections.unmodifiableList(result);
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        lock.tryLock();
        try {
            String result = delegate.getLatestAvailable(prefix);
            return result != null ? PatchId.fromString(result) : null;
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch getPatch(PatchId patchId) {
        lock.tryLock();
        try {
            PatchAdapter result = delegate.getPatch(patchId.toString());
            return result != null ? result.toPatch() : null;
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        return addArchive(fileUrl, false);
    }

    @Override
    public PatchId addArchive(URL fileUrl, boolean force) throws IOException {
        PatchId patchId = PatchId.fromURL(fileUrl);
        DataSource dataSource = new FileDataSource(new File(fileUrl.getPath()));
        PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).build();
        return addArchive(metadata, new DataHandler(dataSource), force);
    }

    @Override
    public PatchId addArchive(PatchMetadata metadata, DataHandler dataHandler, boolean force) throws IOException {
        lock.tryLock();
        try {
            String result = delegate.addArchive(PatchMetadataAdapter.fromPatchMetadata(metadata), dataHandler, force);
            return PatchId.fromString(result);
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeArchive(PatchId removeId) {
        lock.tryLock();
        try {
            return delegate.removeArchive(removeId.toString());
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(Patch seedPatch, PatchId patchId) {
        lock.tryLock();
        try {
            return delegate.getSmartPatch(PatchAdapter.fromPatch(seedPatch), patchId.toString()).toSmartPatch();
        } catch (WebServiceException ex) {
            throw unwrap(ex);
        } finally {
            lock.unlock();
        }
    }

    private RuntimeException unwrap(WebServiceException ex) {
        RuntimeException result = ex;
        String message = ex.getMessage();
        String prefix = SecurityException.class.getName() + ": ";
        if (message.startsWith(prefix)) {
            message = message.substring(prefix.length());
            result = new SecurityException (message);
        }
        return result;
    }
}
