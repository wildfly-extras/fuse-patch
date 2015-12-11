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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

public class RepositoryClient implements Repository {

    private final Lock lock;
    private final RepositoryService delegate;

    public RepositoryClient(Lock lock, QName serviceName, URL wsdlUrl) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        IllegalArgumentAssertion.assertNotNull(serviceName, "serviceName");
        IllegalArgumentAssertion.assertNotNull(wsdlUrl, "wsdlUrl");
        this.lock = lock;
        delegate = Service.create(wsdlUrl, serviceName).getPort(RepositoryService.class);
    }

    @Override
    public URL getBaseURL() {
        return delegate.getBaseURL();
    }

    @Override
    public List<PatchId> queryAvailable(String prefix) {
        lock.tryLock();
        try {
            List<PatchId> result = new ArrayList<>();
            String[] available = delegate.queryAvailable(prefix);
            if (available != null) {
                for (String spec : available) {
                    result.add(PatchId.fromString(spec));
                }
            }
            return Collections.unmodifiableList(result);
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
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl) throws IOException {
        lock.tryLock();
        try {
            PatchId patchId = PatchId.fromURL(fileUrl);
            DataHandler dataHandler = new DataHandler(new URLDataSource(fileUrl));
            PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).build();
            return addArchive(metadata, dataHandler, false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl, boolean force) throws IOException {
        lock.tryLock();
        try {
            PatchId patchId = PatchId.fromURL(fileUrl);
            DataHandler dataHandler = new DataHandler(new URLDataSource(fileUrl));
            PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).build();
            return addArchive(metadata, dataHandler, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(PatchMetadata metadata, DataHandler dataHandler, boolean force) throws IOException {
        lock.tryLock();
        try {
            String result = delegate.addArchive(PatchMetadataAdapter.fromPatchMetadata(metadata), dataHandler, force);
            return PatchId.fromString(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeArchive(PatchId removeId) {
        lock.tryLock();
        try {
            return delegate.removeArchive(removeId.toString());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(Patch seedPatch, PatchId patchId) {
        lock.tryLock();
        try {
            return delegate.getSmartPatch(PatchAdapter.fromPatch(seedPatch), patchId.toString()).toSmartPatch();
        } finally {
            lock.unlock();
        }
    }
}
