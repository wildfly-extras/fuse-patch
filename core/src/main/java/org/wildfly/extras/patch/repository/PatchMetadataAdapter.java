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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;

public class PatchMetadataAdapter {

    private String patchId;
    private String[] roles;
    private String[] dependencySpecs;
    private String[] commands;
    
    public static PatchMetadataAdapter fromPatchMetadata(PatchMetadata metadata) {
    	
    	if (metadata == null)
    		return null;
    	
    	PatchMetadataAdapter result = new PatchMetadataAdapter();
    	result.patchId = metadata.getPatchId().toString();
        
        List<String> roles = new ArrayList<String>(metadata.getRoles());
        result.roles = new String[roles.size()];
        for (int i = 0; i < roles.size(); i++) {
            result.roles[i] = roles.get(i);
        }
        
        List<PatchId> dependencies = new ArrayList<PatchId>(metadata.getDependencies());
        result.dependencySpecs = new String[dependencies.size()];
        for (int i = 0; i < dependencies.size(); i++) {
            result.dependencySpecs[i] = dependencies.get(i).toString();
        }
    	
    	List<String> cmdlist = metadata.getPostCommands();
    	result.commands = new String[cmdlist.size()];
    	cmdlist.toArray(result.commands);
    	return result;
    }
    
    public PatchMetadata toPatchMetadata() {
    	PatchId pid = PatchId.fromString(patchId);
    	Set<PatchId> dependencies = new HashSet<PatchId>();
    	if (dependencySpecs != null) {
        	for (String spec : dependencySpecs) {
        		dependencies.add(PatchId.fromString(spec));
        	}
    	}
    	return new PatchMetadataBuilder().patchId(pid).roles(roles).dependencies(dependencies).postCommands(commands).build();
    }
    
    public String getPatchId() {
		return patchId;
	}

	public void setPatchId(String identity) {
		this.patchId = identity;
	}

	public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String[] getDependencies() {
		return dependencySpecs;
	}

	public void setDependencies(String[] dependencies) {
		this.dependencySpecs = dependencies;
	}

	public String[] getCommands() {
		return commands;
	}

	public void setCommands(String[] commands) {
		this.commands = commands;
	}
}
