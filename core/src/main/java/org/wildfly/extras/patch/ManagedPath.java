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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * A managed server path. 
 *
 * A {@code ManagedPath} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 05-Aug-2015
 */
public final class ManagedPath {

    private final File path;
    private final List<PatchId> owners = new ArrayList<PatchId>();

    public static ManagedPath create(File path, List<PatchId> owners) {
        return new ManagedPath(path, owners);
    }

    public static ManagedPath fromString(String line) {
        IllegalArgumentAssertion.assertNotNull(line, "line");
        int index = line.indexOf(' ');
        List<PatchId> owners = new ArrayList<PatchId>();
        File path = new File(line.substring(0, index));
        String opart = line.substring(index + 1);
        opart = opart.substring(1, opart.length() - 1);
        for (String idspec : opart.split(",")) {
            owners.add(PatchId.fromString(idspec.trim()));
        }
        return new ManagedPath(path, owners);
    }
    
    private ManagedPath(File path, List<PatchId> owners) {
        IllegalArgumentAssertion.assertNotNull(path, "path");
        IllegalArgumentAssertion.assertNotNull(owners, "owners");
        this.path = path;
        this.owners.addAll(owners);
    }

    public File getPath() {
        return path;
    }

    public List<PatchId> getOwners() {
        return Collections.unmodifiableList(owners);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ManagedPath))
            return false;
        ManagedPath other = (ManagedPath) obj;
        return path.equals(other.path) && owners.equals(other.owners);
    }

    @Override
    public String toString() {
        return path + " " + owners;
    }

}