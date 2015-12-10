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

import java.nio.file.Path;
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
 * A package.
 *
 * A package associates a patch id with a list of artefacts ids.
 *
 * A {@code Package} is immutable.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class Package {

    private final PackageMetadata metadata;
    private final Map<Path, Record> recordsMap = new LinkedHashMap<>();
    private int hashCache;

    public static Package create(PatchId patchId, Collection<Record> records) {
        PackageMetadata metadata = new PackageMetadataBuilder().patchId(patchId).build();
        return new Package(metadata, records);
    }

    public static Package create(PackageMetadata metadata, Collection<Record> records) {
        return new Package(metadata, records);
    }

    public static Package smartSet(Package seedPatch, Package targetSet) {
        IllegalArgumentAssertion.assertNotNull(targetSet, "targetSet");

        // All seed patch records are remove candidates
        Map<Path, Record> removeMap = new HashMap<>();
        if (seedPatch != null) {
            for (Record rec : seedPatch.getRecords()) {
                removeMap.put(rec.getPath(), Record.create(null, Action.DEL, rec.getPath(), rec.getChecksum()));
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
                    records.add(Record.create(null, Action.UPD, path, checksum));
                } else {
                    records.add(Record.create(null, Action.ADD, path, checksum));
                }
            }
        }

        records.addAll(removeMap.values());
        return new Package(targetSet.metadata, records);
    }

    private Package(PackageMetadata metadata, Collection<Record> records) {
        IllegalArgumentAssertion.assertNotNull(metadata, "metadata");
        IllegalArgumentAssertion.assertNotNull(records, "records");
        this.metadata = metadata;

        // Sort the records by path
        Map<Path, Record> auxmap = new HashMap<>();
        for (Record aux : records) {
            auxmap.put(aux.getPath(), Record.create(metadata.getPatchId(), aux.getAction(), aux.getPath(), aux.getChecksum()));
        }
        List<Path> paths = new ArrayList<>(auxmap.keySet());
        Collections.sort(paths);
        for (Path path : paths) {
            recordsMap.put(path, auxmap.get(path));
        }
    }

    public PackageMetadata getMetadata() {
        return metadata;
    }

    public PatchId getPatchId() {
        return metadata.getPatchId();
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
        if (!(obj instanceof Package)) return false;
        Package other = (Package) obj;
        boolean result = metadata.equals(other.metadata);
        result &= recordsMap.equals(other.recordsMap);
        return result;
    }

    @Override
    public String toString() {
        return "Package[" + metadata + ",recs=" + recordsMap.size() + "]";
    }
}
