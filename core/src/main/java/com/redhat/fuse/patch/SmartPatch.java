/*
 * #%L
 * Gravia :: Resource
 * %%
 * Copyright (C) 2010 - 2014 JBoss by Red Hat
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
package com.redhat.fuse.patch;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;


/**
 * A smart patch.
 *
 * A smart patch combines remove/replace/add sets. 
 *
 * A {@code SmartPatch} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class SmartPatch {

    private final File patchFile;
    private final PatchId patchId;
    private final Map<Path, ArtefactId> removeMap = new HashMap<>();
    private final Map<Path, ArtefactId> replaceMap = new HashMap<>();
    private final Map<Path, ArtefactId> addMap = new HashMap<>();
    private final Metadata metadata;

    public static class Metadata {
        private final String postScript;
        
        public Metadata(String postScript) {
            this.postScript = postScript;
        }

        public String getPostScript() {
            return postScript;
        }
    }
    
    public SmartPatch(File patchFile, PatchId patchId, Set<ArtefactId> removeSet, Set<ArtefactId> replaceSet, Set<ArtefactId> addSet, Metadata metadata) {
        IllegalArgumentAssertion.assertNotNull(patchFile, "patchFile");
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(removeSet, "removeSet");
        IllegalArgumentAssertion.assertNotNull(replaceSet, "replaceSet");
        IllegalArgumentAssertion.assertNotNull(addSet, "addSet");
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        this.patchId = patchId;
        this.patchFile = patchFile;
        this.metadata = metadata;
        for (ArtefactId aid : removeSet) {
            removeMap.put(aid.getPath(), aid);
        }
        for (ArtefactId aid : replaceSet) {
            replaceMap.put(aid.getPath(), aid);
        }
        for (ArtefactId aid : addSet) {
            addMap.put(aid.getPath(), aid);
        }
    }

    public PatchId getPatchId() {
        return patchId;
    }

    public File getPatchFile() {
        return patchFile;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Set<ArtefactId> getRemoveSet() {
        return Collections.unmodifiableSet(new HashSet<>(removeMap.values()));
    }

    public boolean isRemovePath(Path path) {
        return removeMap.containsKey(path);
    }

	public Set<ArtefactId> getReplaceSet() {
        return Collections.unmodifiableSet(new HashSet<>(replaceMap.values()));
	}

    public boolean isReplacePath(Path path) {
        return replaceMap.containsKey(path);
    }

	public Set<ArtefactId> getAddSet() {
        return Collections.unmodifiableSet(new HashSet<>(addMap.values()));
	}

    public boolean isAddPath(Path path) {
        return addMap.containsKey(path);
    }

    @Override
    public String toString() {
        return "SmartPatch[id=" + patchId + ",file=" + patchFile + ",rem=" + removeMap.size() + ",rep=" + replaceMap.size() + ",add=" + addMap.size() + "]";
    }
}
