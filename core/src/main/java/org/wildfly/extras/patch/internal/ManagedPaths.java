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
package org.wildfly.extras.patch.internal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * A set of managed server paths. 
 *
 * @author thomas.diesler@jboss.com
 * @since 05-Aug-2015
 */
final class ManagedPaths {

    private final Map<Path, ManagedPath> managedPaths = new HashMap<>();

    ManagedPaths(List<ManagedPath> managedPaths) {
        IllegalArgumentAssertion.assertNotNull(managedPaths, "managedPaths");
        for (ManagedPath mpath : managedPaths) {
            this.managedPaths.put(mpath.getPath(), mpath);
        }
    }

    List<ManagedPath> getPaths() {
        List<Path> keys = new ArrayList<>(managedPaths.keySet());
        Collections.sort(keys);
        List<ManagedPath> result = new ArrayList<>();
        for (Path path : keys) {
            result.add(managedPaths.get(path));
        }
        return Collections.unmodifiableList(result);
    }

    ManagedPaths updatePaths(SmartPatch smartPatch) {
        for (Record rec : smartPatch.getRecords()) {
            Action act = rec.getAction();
            if (act == Action.ADD || act == Action.UPD) {
                addPathOwner(rec);
            } else if (act == Action.DEL) {
                removePathOwner(rec);
            }
        }
        return this;
    }

    private void addPathOwner(Record rec) {
        IllegalArgumentAssertion.assertNotNull(rec, "rec");
        Path path = rec.getPath();
        PatchId owner = rec.getPatchId();
        ManagedPath mpath = managedPaths.get(path);
        if (mpath == null) {
            mpath = ManagedPath.create(path, Collections.singletonList(owner));
            managedPaths.put(path, mpath);
        } else {
            List<PatchId> owners = new ArrayList<>(mpath.getOwners());
            removeOwner(owners, owner);
            owners.add(owner);
            mpath = ManagedPath.create(mpath.getPath(), owners);
            managedPaths.put(path, mpath);
        }
    }

    private void removePathOwner(Record rec) {
        IllegalArgumentAssertion.assertNotNull(rec, "rec");
        Path path = rec.getPath();
        PatchId owner = rec.getPatchId();
        ManagedPath mpath = managedPaths.get(path);
        if (mpath != null) {
            List<PatchId> owners = new ArrayList<>(mpath.getOwners());
            removeOwner(owners, owner);
            if (!owners.isEmpty()) {
                mpath = ManagedPath.create(mpath.getPath(), owners);
                managedPaths.put(mpath.getPath(), mpath);
            } else {
                managedPaths.remove(mpath.getPath());
            }
        }
    }

    private void removeOwner(List<PatchId> owners, PatchId owner) {
        Iterator<PatchId> it = owners.iterator();
        while (it.hasNext()) {
            PatchId aux = it.next();
            if (aux.getName().equals(owner.getName())) {
                it.remove();
                break;
            }
        }
    }
}