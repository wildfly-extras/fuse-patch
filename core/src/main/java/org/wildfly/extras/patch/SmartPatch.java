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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;


/**
 * A smart patch defines add/update/delete records. 
 *
 * A {@code SmartPatch} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class SmartPatch implements Closeable {

    private final Patch patch;
    private final DataHandler dataHandler;
    private final Map<File, Record> delMap = new HashMap<File, Record>();
    private final Map<File, Record> updMap = new HashMap<File, Record>();
    private final Map<File, Record> addMap = new HashMap<File, Record>();
    
    public static SmartPatch forInstall(Patch patch, DataHandler dataHandler) {
        IllegalArgumentAssertion.assertNotNull(dataHandler, "dataHandler");
        return new SmartPatch(patch, dataHandler);
    }
    
    public static SmartPatch forUninstall(Patch patch) {
        IllegalArgumentAssertion.assertNotNull(patch, "patch");
        PatchId patchId = patch.getPatchId();
        List<Record> records = new ArrayList<Record>();
        for (Record rec : patch.getRecords()) {
            records.add(Record.create(patchId, Action.DEL, rec.getPath(), rec.getChecksum()));
        }
        return new SmartPatch(Patch.create(patch.getMetadata(), records), null);
    }
    
    private SmartPatch(Patch patch, DataHandler dataHandler) {
        IllegalArgumentAssertion.assertNotNull(patch, "patch");
        this.patch = patch;
        this.dataHandler = dataHandler;
        for (Record rec : patch.getRecords()) {
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
        return patch.getPatchId();
    }

    public Patch getPatch() {
        return patch;
    }

    public DataHandler getDataHandler() {
        return dataHandler;
    }

    public boolean isUninstall() {
        return dataHandler == null;
    }
    
    public List<Record> getRecords() {
        return patch.getRecords();
    }
    
    public PatchMetadata getMetadata() {
        return patch.getMetadata();
    }
    
    public Set<Record> getRemoveSet() {
        return Collections.unmodifiableSet(new HashSet<Record>(delMap.values()));
    }

    public boolean isRemovePath(File path) {
        return delMap.containsKey(path);
    }

	public Set<Record> getReplaceSet() {
        return Collections.unmodifiableSet(new HashSet<Record>(updMap.values()));
	}

    public boolean isReplacePath(File path) {
        return updMap.containsKey(path);
    }

	public Set<Record> getAddSet() {
        return Collections.unmodifiableSet(new HashSet<Record>(addMap.values()));
	}

    public boolean isAddPath(File path) {
        return addMap.containsKey(path);
    }

    @Override
    public void close() throws IOException {
        DataSource dataSource = dataHandler != null ? dataHandler.getDataSource() : null;
        if (dataSource instanceof Closeable) {
            ((Closeable) dataSource).close();
        }
    }

    @Override
    public String toString() {
        return "SmartPatch[id=" + patch.getPatchId() + ",add=" + addMap.size() + ",upd=" + updMap.size() + ",del=" + delMap.size() + "]";
    }
}
