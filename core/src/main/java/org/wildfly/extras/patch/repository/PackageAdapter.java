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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;

public class PackageAdapter {

    private String identity;
    private String[] recordSpecs;
    private String[] dependencySpecs;
    private String[] commandArray;
    
    public static PackageAdapter fromPackage(Package patch) {
    	
    	if (patch == null)
    		return null;
    	
    	PackageAdapter result = new PackageAdapter();
    	result.identity = patch.getPatchId().toString();
    	List<Record> records = patch.getRecords();
		result.recordSpecs = new String[records.size()];
    	for (int i = 0; i < records.size(); i++) {
    		result.recordSpecs[i] = records.get(i).toString();
    	}
    	List<PatchId> dependencies = new ArrayList<>(patch.getDependencies());
    	result.dependencySpecs = new String[dependencies.size()];
    	for (int i = 0; i < dependencies.size(); i++) {
    		result.dependencySpecs[i] = dependencies.get(i).toString();
    	}
    	List<String> commands = patch.getPostCommands();
    	result.commandArray = new String[commands.size()];
    	commands.toArray(result.commandArray);
    	return result;
    }
    
    public Package toPackage() {
    	PatchId patchId = PatchId.fromString(identity);
    	List<Record> records = new ArrayList<>();
    	for (String spec : recordSpecs) {
    		records.add(Record.fromString(spec));
    	}
    	Set<PatchId> dependencies = new HashSet<>();
    	if (dependencySpecs != null) {
        	for (String spec : dependencySpecs) {
        		dependencies.add(PatchId.fromString(spec));
        	}
    	}
    	List<String> commands = new ArrayList<>();
    	if (commandArray != null) {
    		commands = Arrays.asList(commandArray);
    	}
    	return Package.create(patchId, records, dependencies, commands);
    }
    
    public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public String[] getRecords() {
		return recordSpecs;
	}

	public void setRecords(String[] records) {
		this.recordSpecs = records;
	}

	public String[] getDependencies() {
		return dependencySpecs;
	}

	public void setDependencies(String[] dependencies) {
		this.dependencySpecs = dependencies;
	}

	public String[] getCommands() {
		return commandArray;
	}

	public void setCommands(String[] commands) {
		this.commandArray = commands;
	}
}
