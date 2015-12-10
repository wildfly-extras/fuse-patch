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

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.Record;

public class PackageAdapter {

    private PackageMetadataAdapter metadataSpec;
    private String[] recordSpecs;
    
    public static PackageAdapter fromPackage(Package patch) {
    	
    	if (patch == null)
    		return null;
    	
    	PackageAdapter result = new PackageAdapter();
    	result.metadataSpec = PackageMetadataAdapter.fromPackage(patch.getMetadata());
    	List<Record> records = patch.getRecords();
		result.recordSpecs = new String[records.size()];
    	for (int i = 0; i < records.size(); i++) {
    		result.recordSpecs[i] = records.get(i).toString();
    	}
    	return result;
    }
    
    public Package toPackage() {
        PackageMetadata metadata = metadataSpec.toPackageMetadata();
    	List<Record> records = new ArrayList<>();
    	for (String spec : recordSpecs) {
    		records.add(Record.fromString(spec));
    	}
    	return Package.create(metadata, records);
    }
    
	public PackageMetadataAdapter getMetadata() {
        return metadataSpec;
    }

    public void setMetadata(PackageMetadataAdapter metadata) {
        this.metadataSpec = metadata;
    }

    public String[] getRecords() {
		return recordSpecs;
	}

	public void setRecords(String[] records) {
		this.recordSpecs = records;
	}
}
