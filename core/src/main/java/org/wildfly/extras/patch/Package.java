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

import static org.wildfly.extras.patch.Record.Action.ADD;
import static org.wildfly.extras.patch.Record.Action.DEL;
import static org.wildfly.extras.patch.Record.Action.UPD;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * A package.
 *
 * A package associates a patch id with a list of artefacts ids. 
 *
 * A {@code Package} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class Package {

    private final Identity identity;
    private final Map<Path, Record> recordsMap = new LinkedHashMap<>();
    private final List<String> commands = new ArrayList<>();
    private final Set<Identity> dependencies = new LinkedHashSet<>();
    private int hashCache;

    public static Package create(Identity patchId, Collection<Record> records) {
        return new Package(patchId, records, Collections.<Identity>emptySet(), Collections.<String>emptyList());
    }

    public static Package create(Identity patchId, Collection<Record> records, Set<Identity> dependencies) {
        return new Package(patchId, records, dependencies, Collections.<String>emptyList());
    }

    public static Package create(Identity patchId, Collection<Record> records, List<String> commands) {
        return new Package(patchId, records, Collections.<Identity>emptySet(), commands);
    }

    public static Package create(Identity patchId, Collection<Record> records, Set<Identity> dependencies, List<String> commands) {
        return new Package(patchId, records, dependencies, commands);
    }

    public static Package smartSet(Package seedPatch, Package targetSet) {
        IllegalArgumentAssertion.assertNotNull(targetSet, "targetSet");

        // All seed patch records are remove candidates
        Map<Path, Record> removeMap = new HashMap<>();
        if (seedPatch != null) {
            for (Record rec : seedPatch.getRecords()) {
                removeMap.put(rec.getPath(), Record.create(DEL, rec.getPath(), rec.getChecksum()));
            }
        }
        
        Set<Record> records = new HashSet<>();
        for (Record rec : targetSet.getRecords()) {
            Path path = rec.getPath();
            Long checksum = rec.getChecksum();
            if (removeMap.containsValue(rec)) {
                removeMap.remove(path);
            } else {
                if (removeMap.containsKey(path)) {
                    removeMap.remove(path);
                    records.add(Record.create(UPD, path, checksum));
                } else {
                    records.add(Record.create(ADD, path, checksum));
                }
            }
        }
        
        records.addAll(removeMap.values());
        return new Package(targetSet.identity, records, targetSet.dependencies, targetSet.commands);
    }

    private Package(Identity patchId, Collection<Record> records, Set<Identity> dependencies, List<String> commands) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(records, "records");
        IllegalArgumentAssertion.assertNotNull(dependencies, "dependencies");
        IllegalArgumentAssertion.assertNotNull(commands, "commands");
        this.dependencies.addAll(dependencies);
        this.commands.addAll(commands);
        this.identity = patchId;

        // Sort the artefacts by path
        Map<Path, Record> auxmap = new HashMap<>();
        for (Record rec : records) {
            auxmap.put(rec.getPath(), rec);
        }
        List<Path> paths = new ArrayList<>(auxmap.keySet());
        Collections.sort(paths);
        for (Path path : paths) {
            recordsMap.put(path, auxmap.get(path));
        }
    }

    public Identity getPatchId() {
        return identity;
    }

    public List<Record> getRecords() {
        return Collections.unmodifiableList(new ArrayList<>(recordsMap.values()));
    }

    public boolean containsPath(Path path) {
        return recordsMap.containsKey(path);
    }

    public Record getRecord(Path path) {
        return recordsMap.get(path);
    }

    public Set<Identity> getDependencies() {
        return dependencies;
    }

    public List<String> getPostCommands() {
        return Collections.unmodifiableList(commands);
    }

    @Override
    public int hashCode() {
        if (hashCache == 0) {
            hashCache = ("" + identity + recordsMap + commands).hashCode();
        }
        return hashCache;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Package)) return false;
        Package other = (Package) obj;
        boolean result = identity.equals(other.identity);
        result &= recordsMap.equals(other.recordsMap);
        result &= dependencies.equals(other.dependencies);
        result &= commands.equals(other.commands);
        return result;
    }

    @Override
    public String toString() {
        return "PatchSet[" + identity + ",recs=" + recordsMap.size() + ",deps=" + dependencies + ",cmds=" + commands.size() + "]";
    }
}
