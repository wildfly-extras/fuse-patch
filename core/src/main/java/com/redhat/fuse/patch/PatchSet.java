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
package com.redhat.fuse.patch;

import static com.redhat.fuse.patch.PatchSet.Action.ADD;
import static com.redhat.fuse.patch.PatchSet.Action.DEL;
import static com.redhat.fuse.patch.PatchSet.Action.INFO;
import static com.redhat.fuse.patch.PatchSet.Action.UPD;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;
import com.redhat.fuse.patch.utils.IllegalStateAssertion;

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
    private final Map<Path, Record> recordsMap = new LinkedHashMap<>();
    private final List<String> commands = new ArrayList<>();

    public static enum Action {
        INFO, ADD, UPD, DEL
    };

    public static PatchSet create(PatchId patchId, Collection<Record> records) {
        return new PatchSet(patchId, records, Collections.<String>emptyList());
    }

    public static PatchSet create(PatchId patchId, Collection<Record> records, List<String> commands) {
        return new PatchSet(patchId, records, commands);
    }

    public static PatchSet smartSet(PatchSet seedPatch, PatchSet targetSet) {
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
        return new PatchSet(targetSet.getPatchId(), records, targetSet.getPostCommands());
    }

    private PatchSet(PatchId patchId, Collection<Record> records, List<String> commands) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        IllegalArgumentAssertion.assertNotNull(records, "records");
        IllegalArgumentAssertion.assertNotNull(commands, "commands");
        this.commands.addAll(commands);
        this.patchId = patchId;

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

    public PatchId getPatchId() {
        return patchId;
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

    public List<String> getPostCommands() {
        return Collections.unmodifiableList(commands);
    }

    @Override
    public String toString() {
        return "PatchSet[" + patchId + ",recs=" + recordsMap.size() + ",cmds=" + commands.size() + "]";
    }

    public final static class Record {

        private final Action action;
        private final Path path;
        private final Long checksum;

        public static Record create(Path path) {
            return new Record(INFO, path, 0L);
        }

        public static Record create(Path path, Long checksum) {
            return new Record(INFO, path, checksum);
        }

        public static Record create(Action action, Path path, Long checksum) {
            return new Record(action, path, checksum);
        }

        public static Record fromString(String line) {
            IllegalArgumentAssertion.assertNotNull(line, "line");
            String[] toks = line.split("[\\s]");
            IllegalStateAssertion.assertEquals(3, toks.length, "Invalid line: " + line);
            return new Record(Action.valueOf(toks[0]), Paths.get(toks[1]), new Long(toks[2]));
        }
        
        private Record(Action action, Path path, Long checksum) {
            IllegalArgumentAssertion.assertNotNull(action, "action");
            IllegalArgumentAssertion.assertNotNull(path, "path");
            IllegalArgumentAssertion.assertNotNull(checksum, "checksum");
            this.action = action;
            this.path = path;
            this.checksum = checksum;
        }

        public Action getAction() {
            return action;
        }

        public Path getPath() {
            return path;
        }

        public Long getChecksum() {
            return checksum;
        }

        @Override
        public int hashCode() {
            return ("" + path + checksum).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Record))
                return false;
            Record other = (Record) obj;
            return path.equals(other.path) && checksum.equals(other.checksum);
        }

        @Override
        public String toString() {
            return action + " " + path + " " + checksum;
        }
    }
}
