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
package org.wildfly.extras.patch.repository;

import java.util.ArrayList;
import java.util.List;

import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.Record;

public class PatchAdapter {

    private PatchMetadataAdapter metadataSpec;
    private String[] recordSpecs;
    
    public static PatchAdapter fromPatch(Patch patch) {
    	
    	if (patch == null)
    		return null;
    	
    	PatchAdapter result = new PatchAdapter();
    	result.metadataSpec = PatchMetadataAdapter.fromPatchMetadata(patch.getMetadata());
    	List<Record> records = patch.getRecords();
		result.recordSpecs = new String[records.size()];
    	for (int i = 0; i < records.size(); i++) {
    		result.recordSpecs[i] = records.get(i).toString();
    	}
    	return result;
    }
    
    public Patch toPatch() {
        PatchMetadata metadata = metadataSpec.toPatchMetadata();
    	List<Record> records = new ArrayList<Record>();
    	for (String spec : recordSpecs) {
    		records.add(Record.fromString(spec));
    	}
    	return Patch.create(metadata, records);
    }
    
	public PatchMetadataAdapter getMetadata() {
        return metadataSpec;
    }

    public void setMetadata(PatchMetadataAdapter metadata) {
        this.metadataSpec = metadata;
    }

    public String[] getRecords() {
		return recordSpecs;
	}

	public void setRecords(String[] records) {
		this.recordSpecs = records;
	}
}
