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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * {@code PatchMetadata} are immutable.
 */
public final class PatchMetadata {

    private final PatchId patchId;
    private final PatchId oneoffId;
    private final Set<String> roles = new LinkedHashSet<String>();
    private final Set<PatchId> dependencies = new LinkedHashSet<PatchId>();
    private final List<String> commands = new ArrayList<String>();
    private final String stringCache;
    
    PatchMetadata(PatchId patchId, Set<String> roles, PatchId oneoffId, Set<PatchId> dependencies, List<String> commands) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        this.patchId = patchId;
        this.oneoffId = oneoffId;
        if (roles != null) {
            this.roles.addAll(roles);
        }
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
        }
        if (commands != null) {
            this.commands.addAll(commands);
        }
        this.stringCache = "[" + patchId + ",roles=" + roles + ",oneoff=" + oneoffId + ",deps=" + dependencies + ",cmds=" + commands + "]"; 
    }

    public PatchId getPatchId() {
        return patchId;
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public PatchId getOneoffId() {
        return oneoffId;
    }


    public Set<PatchId> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }


    public List<String> getPostCommands() {
        return Collections.unmodifiableList(commands);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PatchMetadata)) return false;
        PatchMetadata other = (PatchMetadata) obj;
        return toString().equals(other.toString());
    }

    @Override
    public String toString() {
        return stringCache;
    }
}
