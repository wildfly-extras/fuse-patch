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
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.wildfly.extras.patch.Package;
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
    public Package getPackage(PatchId patchId) {
        lock.tryLock();
        try {
            PackageAdapter result = delegate.getPackage(patchId.toString());
            return result != null ? result.toPackage() : null;
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
            return addArchive(patchId, dataHandler, null, null, false);
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
            return addArchive(patchId, dataHandler, null, null, force);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(URL fileUrl, PatchId oneoffId) throws IOException {
        lock.tryLock();
        try {
            PatchId patchId = PatchId.fromURL(fileUrl);
            DataHandler dataHandler = new DataHandler(new URLDataSource(fileUrl));
            return addArchive(patchId, dataHandler, oneoffId, null, false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PatchId addArchive(PatchId patchId, DataHandler dataHandler, PatchId oneoffId, Set<PatchId> dependencies, boolean force) throws IOException {
        lock.tryLock();
        try {
            String oneoffSpec = oneoffId != null ? oneoffId.toString() : null;
            String deps[] = new String[0];
            if (dependencies != null) {
                List<PatchId> deplist = new ArrayList<>(dependencies);
                deps = new String[dependencies.size()];
                for (int i = 0; i < dependencies.size(); i++) {
                    deps[i] = deplist.get(i).toString();
                }
            }
            String result = delegate.addArchive(patchId.toString(), dataHandler, oneoffSpec, deps, force);
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
    public void addPostCommand(PatchId patchId, String[] cmdarr) {
        lock.tryLock();
        try {
            delegate.addPostCommand(patchId.toString(), cmdarr);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SmartPatch getSmartPatch(Package seedPatch, PatchId patchId) {
        lock.tryLock();
        try {
            return delegate.getSmartPatch(PackageAdapter.fromPackage(seedPatch), patchId.toString()).toSmartPatch();
        } finally {
            lock.unlock();
        }
    }
}
