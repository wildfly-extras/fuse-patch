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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * A patch associates metadata with a set of records.
 *
 * A {@code Patch} is immutable.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class Patch {

    private final PatchMetadata metadata;
    private final Map<File, Record> recordsMap = new LinkedHashMap<File, Record>();
    private int hashCache;

    public static Patch create(PatchMetadata metadata, Collection<Record> records) {
        return new Patch(metadata, records);
    }

    public static Patch smartDelta(Patch seedPatch, Patch targetSet) {
        IllegalArgumentAssertion.assertNotNull(targetSet, "targetSet");

        // All seed patch records are remove candidates
        Map<File, Record> removeMap = new HashMap<File, Record>();
        if (seedPatch != null) {
            for (Record rec : seedPatch.getRecords()) {
                removeMap.put(rec.getPath(), Record.create(null, Action.DEL, rec.getPath(), rec.getChecksum()));
            }
        }

        Set<Record> records = new HashSet<Record>();
        for (Record rec : targetSet.getRecords()) {
            File path = rec.getPath();
            Long checksum = rec.getChecksum();
            if (removeMap.containsValue(rec)) {
                removeMap.remove(path);
            } else {
                if (removeMap.containsKey(path)) {
                    records.add(Record.create(null, Action.UPD, path, checksum));
                    removeMap.remove(path);
                } else {
                    records.add(Record.create(null, Action.ADD, path, checksum));
                }
            }
        }

        records.addAll(removeMap.values());
        return new Patch(targetSet.metadata, records);
    }

    private Patch(PatchMetadata metadata, Collection<Record> records) {
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        IllegalArgumentAssertion.assertNotNull(records, "records");
        this.metadata = metadata;

        // Sort the records by path
        Map<File, Record> auxmap = new HashMap<File, Record>();
        for (Record aux : records) {
            auxmap.put(aux.getPath(), Record.create(metadata.getPatchId(), aux.getAction(), aux.getPath(), aux.getChecksum()));
        }
        List<File> paths = new ArrayList<File>(auxmap.keySet());
        Collections.sort(paths);
        for (File path : paths) {
            recordsMap.put(path, auxmap.get(path));
        }
    }

    public PatchMetadata getMetadata() {
        return metadata;
    }

    public PatchId getPatchId() {
        return metadata.getPatchId();
    }

    public List<Record> getRecords() {
        return Collections.unmodifiableList(new ArrayList<Record>(recordsMap.values()));
    }

    public boolean containsPath(File path) {
        return recordsMap.containsKey(path);
    }

    public Record getRecord(File path) {
        return recordsMap.get(path);
    }

    @Override
    public int hashCode() {
        if (hashCache == 0) {
            hashCache = ("" + metadata + recordsMap).hashCode();
        }
        return hashCache;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Patch)) return false;
        Patch other = (Patch) obj;
        boolean result = metadata.equals(other.metadata);
        result &= recordsMap.equals(other.recordsMap);
        return result;
    }

    @Override
    public String toString() {
        return "Patch[" + metadata + ",recs=" + recordsMap.size() + "]";
    }
}
