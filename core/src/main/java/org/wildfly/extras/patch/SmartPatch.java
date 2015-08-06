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

import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;


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

    private final URL patchURL;
    private final Package patchSet;
    private final Map<Path, Record> delMap = new HashMap<>();
    private final Map<Path, Record> updMap = new HashMap<>();
    private final Map<Path, Record> addMap = new HashMap<>();
    
    public static SmartPatch forInstall(Package patchSet, URL patchURL) {
        IllegalArgumentAssertion.assertNotNull(patchURL, "patchURL");
        return new SmartPatch(patchSet, patchURL);
    }
    
    public static SmartPatch forUninstall(Package patchSet) {
        return new SmartPatch(patchSet, null);
    }
    
    private SmartPatch(Package patchSet, URL patchURL) {
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        this.patchSet = patchSet;
        this.patchURL = patchURL;
        for (Record rec : patchSet.getRecords()) {
            Record.Action action = rec.getAction();
            switch (rec.getAction()) {
                case ADD:
                    addMap.put(rec.getPath(), rec);
                    break;
                case UPD:
                    updMap.put(rec.getPath(), rec);
                    break;
                case DEL:
                    delMap.put(rec.getPath(), rec);
                    break;
                default:
                    throw new IllegalStateException(action + " no supported");
            }
        }
    }

    public PatchId getPatchId() {
        return patchSet.getPatchId();
    }

    public URL getPatchURL() {
        return patchURL;
    }

    public boolean isUninstall() {
        return patchURL == null;
    }
    
    public List<Record> getRecords() {
        return patchSet.getRecords();
    }
    
    public Set<PatchId> getDependencies() {
        return patchSet.getDependencies();
    }
    
    public Set<Record> getRemoveSet() {
        return Collections.unmodifiableSet(new HashSet<>(delMap.values()));
    }

    public boolean isRemovePath(Path path) {
        return delMap.containsKey(path);
    }

	public Set<Record> getReplaceSet() {
        return Collections.unmodifiableSet(new HashSet<>(updMap.values()));
	}

    public boolean isReplacePath(Path path) {
        return updMap.containsKey(path);
    }

	public Set<Record> getAddSet() {
        return Collections.unmodifiableSet(new HashSet<>(addMap.values()));
	}

    public boolean isAddPath(Path path) {
        return addMap.containsKey(path);
    }

    public List<String> getPostCommands() {
        return patchSet.getPostCommands();
    }
    
    @Override
    public String toString() {
        return "SmartPatch[id=" + patchSet.getPatchId() + ",url=" + patchURL + ",add=" + addMap.size() + ",upd=" + updMap.size() + ",del=" + delMap.size() + "]";
    }
}
