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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;


/**
 * A patch set.
 *
 * A patch set associates a patch id with a list of artefacts ids. 
 *
 * A {@code PatchSet} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class PatchSet {

    private final PatchId patchId;
    private final Map<Path, ArtefactId> artefactMap = new LinkedHashMap<>();

    public PatchSet(PatchId patchId, Set<ArtefactId> artefacts) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(artefacts, "artefacts");
        this.patchId = patchId;
        
        // Sort the artefacts by path
        Map<Path, ArtefactId> auxmap = new HashMap<>();
        for (ArtefactId artefactId : artefacts) {
            auxmap.put(artefactId.getPath(), artefactId);
        }
        List<Path> paths = new ArrayList<>(auxmap.keySet());
        Collections.sort(paths);
        for (Path path : paths) {
            artefactMap.put(path, auxmap.get(path));
        }
    }

    public PatchId getPatchId() {
		return patchId;
	}

	public Set<ArtefactId> getArtefacts() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(artefactMap.values()));
	}

    public boolean containsPath(Path path) {
        return artefactMap.containsKey(path);
    }
    
    public ArtefactId getArtefact(Path path) {
        return artefactMap.get(path);
    }
    
	@Override
    public String toString() {
        return "PatchSet[" + patchId + "," + artefactMap.size() + "]";
    }
}
