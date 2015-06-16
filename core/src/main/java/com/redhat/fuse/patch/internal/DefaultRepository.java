/*
 * #%L
 * Fuse Patch :: Parser
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
package com.redhat.fuse.patch.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.redhat.fuse.patch.ArtefactId;
import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.Repository;
import com.redhat.fuse.patch.SmartPatch;
import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;
import com.redhat.fuse.patch.utils.IllegalStateAssertion;

public final class DefaultRepository implements Repository {

    private final Path rootPath;

    public DefaultRepository(URL repoUrl) {
        Path path = repoUrl != null ? Paths.get(repoUrl.getPath()) : inferRootPath();
        IllegalStateAssertion.assertTrue(path.toFile().isDirectory(), "Not a valid root directory: " + path);
        this.rootPath = path.toAbsolutePath();
    }

    @Override
    public List<PatchId> queryAvailable(final String prefix) {
        final List<PatchId> result = new ArrayList<>();
        rootPath.toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if ((prefix == null || name.startsWith(prefix)) && name.endsWith(".zip")) {
                    name = name.substring(0, name.lastIndexOf('.'));
                    result.add(PatchId.fromString(name));
                }
                return false;
            }
        });
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    @Override
    public PatchId getLatestAvailable(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        List<PatchId> list = queryAvailable(prefix);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    @Override
    public PatchId addArchive(Path filePath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(filePath, "filePath");
        Path fileName = filePath.getFileName();
        PatchId result = PatchId.fromString(fileName.toString());
        Path targetPath = rootPath.resolve(fileName);
        Files.copy(filePath, targetPath);
        return result;
    }

    @Override
    public SmartPatch getSmartPatch(PatchSet seedPatch, PatchId patchId) {

        // Derive the target patch id from the seed patch id
        if (patchId == null) {
            IllegalArgumentAssertion.assertNotNull(seedPatch, "seedPatch");
            patchId = getLatestAvailable(seedPatch.getPatchId().getSymbolicName());
        }

        // Get the patch zip file
        File zipfile = rootPath.resolve(patchId.getCanonicalForm() + ".zip").toFile();
        IllegalStateAssertion.assertTrue(zipfile.isFile(), "Cannot obtain patch file: " + zipfile);

        Map<Path, ArtefactId> removeMap = new HashMap<>();
        Set<ArtefactId> replaceSet = new HashSet<>();
        Set<ArtefactId> addSet = new HashSet<>();

        // All seed patch artefacts are remove candidates
        if (seedPatch != null) {
            for (ArtefactId artefactId : seedPatch.getArtefacts()) {
                removeMap.put(artefactId.getPath(), artefactId);
            }
        }

        try {
            Parser.Metadata metadata = new Parser().buildMetadata(zipfile);
            for (Entry<String, Long> entry : metadata.getEntries().entrySet()) {
                String path = entry.getKey();
                Long checksum = entry.getValue();
                ArtefactId artefactId = ArtefactId.create(Paths.get(path), checksum);
                if (removeMap.containsValue(artefactId)) {
                    removeMap.remove(artefactId.getPath());
                } else {
                    if (removeMap.containsKey(artefactId.getPath())) {
                        removeMap.remove(artefactId.getPath());
                        replaceSet.add(artefactId);
                    } else {
                        addSet.add(artefactId);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        Set<ArtefactId> removeSet = new HashSet<>(removeMap.values());
        
        Path metadataPath = rootPath.resolve(patchId + ".metadata");
        SmartPatch.Metadata metadata = parseMetadata(metadataPath);
        
        return new SmartPatch(zipfile, patchId, removeSet, replaceSet, addSet, metadata);
    }

    public SmartPatch.Metadata parseMetadata(Path metadataPath) {
        String postScript = null;
        if (metadataPath.toFile().exists()) {
            try{
                String mode = null;
                BufferedReader br = new BufferedReader(new FileReader(metadataPath.toFile()));
                try {
                    String line = br.readLine();
                    while (line != null) {
                        line = line.trim();
                        if (line.length() == 0 || line.startsWith("#")) {
                            line = br.readLine();
                            continue;
                        }
                        if (line.startsWith("[") && line.endsWith("]")) {
                            mode = line;
                            line = br.readLine();
                            continue;
                        }
                        if ("[post-install-script]".equals(mode)) {
                            postScript = line;
                            mode = null;
                        }
                        line = br.readLine();
                    }
                } finally {
                    br.close();
                }
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return new SmartPatch.Metadata(postScript);
    }

    private Path inferRootPath() {
        throw new IllegalStateException("Cannot infer patch repository location");
    }
}
