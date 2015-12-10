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
package org.wildfly.extras.patch.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.wildfly.extras.patch.PackageMetadata;
import org.wildfly.extras.patch.PackageMetadataBuilder;
import org.wildfly.extras.patch.PatchId;

/**
 * Data model for repository add operations
 * 
 *   <package>
 *       <patchId>foo-1.0.0.SP1</patchId>
 *       <oneoffId>foo-1.0.0</oneoffId>
 *       <dependencies>
 *           <patchId>aaa-0.0.0</patchId>
 *       </dependencies>
 *       <post-commands>
 *           <command>echo done</command>
 *       </post-commands>
 *   </package>
 */
@XmlType(propOrder = { "patchId", "oneoffId", "dependencies", "postCommands" })
@XmlRootElement(name = "package")
public final class PackageMetadataModel {

    private String patchId;
    private String oneoffId;
    private Dependencies dependencies;
    private Commands postCommands;

    public static PackageMetadataModel fromPackageMetadata(PackageMetadata metadata) {
        PackageMetadataModel model = new PackageMetadataModel();
        model.patchId = metadata.getPatchId().toString();
        model.oneoffId = metadata.getOneoffId() != null ? metadata.getOneoffId().toString() : null;
        model.dependencies = new Dependencies(metadata.getDependencies());
        model.postCommands = new Commands(metadata.getPostCommands());
        return model;
    }
    
    public PackageMetadata toPackageMetadata() {
        PackageMetadataBuilder builder = new PackageMetadataBuilder().patchId(PatchId.fromString(patchId));
        if (oneoffId != null) {
            builder.oneoffId(PatchId.fromString(oneoffId));
        }
        if (dependencies != null) {
            for (String auxid : dependencies.getDependencies()) {
                builder.dependencies(PatchId.fromString(auxid));
            }
        }
        if (postCommands != null) {
            builder.postCommands(postCommands.getCommands());
        }
        return builder.build();
    }
    
    public String getPatchId() {
        return patchId;
    }

    @XmlElement(name = "patchId")
    public void setPatchId(String patchId) {
        this.patchId = patchId;
    }

    public String getOneoffId() {
        return oneoffId;
    }

    @XmlElement(name = "oneoffId")
    public void setOneoffId(String oneoffId) {
        this.oneoffId = oneoffId;
    }

    public Dependencies getDependencies() {
        return dependencies;
    }

    @XmlElement(name = "dependencies")
    public void setDependencies(Dependencies dependencies) {
        this.dependencies = dependencies;
    }

    public Commands getPostCommands() {
        return postCommands;
    }

    @XmlElement(name = "post-commands")
    public void setPostCommands(Commands postCommands) {
        this.postCommands = postCommands;
    }

    @XmlType
    public static class Dependencies {
        
        private Set<String> patchIds;
        
        public Dependencies() {
        }

        public Dependencies(Set<PatchId> dependencies) {
            patchIds = new LinkedHashSet<> ();
            for (PatchId aux : dependencies) {
                patchIds.add(aux.toString());
            }
        }

        public Set<String> getDependencies() {
            return patchIds;
        }

        @XmlElement(name = "patchId")
        void setDependencies(Set<String> patchIds) {
            this.patchIds = patchIds;
        }
    }

    @XmlType
    public static class Commands {
        
        private List<String> commands;

        public Commands() {
        }

        public Commands(List<String> commands) {
            this.commands = new ArrayList<>(commands);
        }

        public List<String> getCommands() {
            return commands;
        }

        @XmlElement(name = "command")
        public void setCommands(List<String> commands) {
            this.commands = commands;
        }
    }
}
