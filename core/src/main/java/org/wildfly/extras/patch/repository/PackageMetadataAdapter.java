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

import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.PackageMetadataBuilder;
import org.wildfly.extras.patch.PatchId;

public class PackageMetadataAdapter {

    private String identity;
    private String[] dependencySpecs;
    private String[] commandArray;
    
    public static PackageMetadataAdapter fromPackage(PackageMetadata metadata) {
    	
    	if (metadata == null)
    		return null;
    	
    	PackageMetadataAdapter result = new PackageMetadataAdapter();
    	result.identity = metadata.getPatchId().toString();
    	List<PatchId> dependencies = new ArrayList<>(metadata.getDependencies());
    	result.dependencySpecs = new String[dependencies.size()];
    	for (int i = 0; i < dependencies.size(); i++) {
    		result.dependencySpecs[i] = dependencies.get(i).toString();
    	}
    	List<String> commands = metadata.getPostCommands();
    	result.commandArray = new String[commands.size()];
    	commands.toArray(result.commandArray);
    	return result;
    }
    
    public PackageMetadata toPackageMetadata() {
    	PatchId patchId = PatchId.fromString(identity);
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
    	return new PackageMetadataBuilder().patchId(patchId).dependencies(dependencies).postCommands(commands).build();
    }
    
    public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
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
