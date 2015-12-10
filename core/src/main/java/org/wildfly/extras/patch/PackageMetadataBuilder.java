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

public final class PackageMetadataBuilder {

    private PatchId patchId;
    private PatchId oneoffId;
    private Set<PatchId> dependencies = new LinkedHashSet<>();
    private List<String> postCommands = new ArrayList<>();

    public PackageMetadataBuilder patchId(PatchId patchId) {
        this.patchId = patchId;
        return this;
    }

    public PackageMetadataBuilder oneoffId(PatchId oneoffId) {
        this.oneoffId = oneoffId;
        return this;
    }

    public PackageMetadataBuilder dependencies(PatchId... patchIds) {
        if (patchIds != null) {
            dependencies.addAll(Arrays.asList(patchIds));
        }
        return this;
    }

    public PackageMetadataBuilder dependencies(Set<PatchId> patchIds) {
        if (patchIds != null) {
            dependencies.addAll(patchIds);
        }
        return this;
    }

    public PackageMetadataBuilder postCommands(String... commands) {
        if (commands != null) {
            postCommands.addAll(Arrays.asList(commands));
        }
        return this;
    }

    public PackageMetadataBuilder postCommands(List<String> commands) {
        if (commands != null) {
            postCommands.addAll(commands);
        }
        return this;
    }

    public PackageMetadata build() {
        return new PackageMetadata(patchId, oneoffId, dependencies, postCommands);
    }
}
