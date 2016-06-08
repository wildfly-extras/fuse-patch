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

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

/**
 * A record associates an action with a file path and a checksum. 
 *
 * A {@code Record} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class Record {

    public static enum Action {
        INFO, ADD, UPD, DEL
    }

    private final PatchId patchId;
    private final Record.Action action;
    private final File path;
    private final Long checksum;

    public static Record create(File path) {
        return new Record(null, Action.INFO, path, 0L);
    }

    public static Record create(File path, Long checksum) {
        return new Record(null, Action.INFO, path, checksum);
    }
    
    public static Record create(PatchId patchId, Action action, File path, Long checksum) {
        return new Record(patchId, action, path, checksum);
    }

    public static Record fromString(String line) {
        IllegalArgumentAssertion.assertNotNull(line, "line");
        String[] toks = line.split("[\\s]");
        IllegalStateAssertion.assertEquals(3, toks.length, "Invalid line: " + line);
        return new Record(null, Record.Action.valueOf(toks[0]), new File(toks[1]), new Long(toks[2]));
    }
    
    private Record(PatchId patchId, Action action, File path, Long checksum) {
        IllegalArgumentAssertion.assertNotNull(action, "action");
        IllegalArgumentAssertion.assertNotNull(path, "path");
        IllegalArgumentAssertion.assertNotNull(checksum, "checksum");
        this.patchId = patchId;
        this.action = action;
        this.path = path;
        this.checksum = checksum;
    }

    public PatchId getPatchId() {
        return patchId;
    }

    public Record.Action getAction() {
        return action;
    }

    public File getPath() {
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