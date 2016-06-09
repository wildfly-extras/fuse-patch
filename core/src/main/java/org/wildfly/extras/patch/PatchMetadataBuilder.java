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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PatchMetadataBuilder {

    private PatchId patchId;
    private PatchId oneoffId;
    private Set<String> roles = new LinkedHashSet<String>();
    private Set<PatchId> dependencies = new LinkedHashSet<PatchId>();
    private List<String> postCommands = new ArrayList<String>();

    public PatchMetadataBuilder patchId(PatchId patchId) {
        this.patchId = patchId;
        return this;
    }

    public PatchMetadataBuilder roles(String... roles) {
        if (roles != null) {
            this.roles.addAll(Arrays.asList(roles));
        }
        return this;
    }
    
    public PatchMetadataBuilder roles(Set<String> roles) {
        if (roles != null) {
            this.roles.addAll(roles);
        }
        return this;
    }
    
    public PatchMetadataBuilder oneoffId(PatchId oneoffId) {
        this.oneoffId = oneoffId;
        return this;
    }

    public PatchMetadataBuilder dependencies(PatchId... patchIds) {
        if (patchIds != null) {
            dependencies.addAll(Arrays.asList(patchIds));
        }
        return this;
    }

    public PatchMetadataBuilder dependencies(Set<PatchId> patchIds) {
        if (patchIds != null) {
            dependencies.addAll(patchIds);
        }
        return this;
    }

    public PatchMetadataBuilder postCommands(String... commands) {
        if (commands != null) {
            postCommands.addAll(Arrays.asList(commands));
        }
        return this;
    }

    public PatchMetadataBuilder postCommands(List<String> commands) {
        if (commands != null) {
            postCommands.addAll(commands);
        }
        return this;
    }

    public PatchMetadata build() {
        return new PatchMetadata(patchId, roles, oneoffId, dependencies, postCommands);
    }
}
