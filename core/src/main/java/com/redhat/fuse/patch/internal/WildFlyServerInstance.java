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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.fuse.patch.ArtefactId;
import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.PatchSet;
import com.redhat.fuse.patch.ServerInstance;
import com.redhat.fuse.patch.SmartPatch;
import com.redhat.fuse.patch.internal.Parser.Metadata;
import com.redhat.fuse.patch.utils.IOUtils;
import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;
import com.redhat.fuse.patch.utils.IllegalStateAssertion;

public final class WildFlyServerInstance implements ServerInstance {

    private static final Logger LOG = LoggerFactory.getLogger(WildFlyServerInstance.class);

    private static final String FUSEPATCH_LATEST = "fusepatch.latest";
    private static final String FUSEPATCH_PREFIX = "fpatch-";
    
    private final Path homePath;

    public WildFlyServerInstance(Path homePath) {
        Path path = homePath != null ? homePath : inferServerHome();
        IllegalStateAssertion.assertTrue(path.toFile().isDirectory(), "Not a valid server directory: " + path);
        this.homePath = path.toAbsolutePath();
    }

    public Path getServerHome() {
        return homePath;
    }

    @Override
    public List<PatchId> queryAppliedPatches() {
        final List<PatchId> result = new ArrayList<>();
        getWorkspace().toFile().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.startsWith(FUSEPATCH_PREFIX)) {
                    String idspec = name.substring(FUSEPATCH_PREFIX.length());
                    result.add(PatchId.fromString(idspec));
                }
                return false;
            }
        });
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    @Override
    public PatchSet getAppliedPatchSet(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        
        Path patchdir = getPatchDir(patchId);
        IllegalStateAssertion.assertTrue(patchdir.toFile().exists(), "Path does not exist: " + patchdir);
        
        Metadata metadata;
        try {
            File mdfile = patchdir.resolve(patchId + ".metadata").toFile();
            metadata = Parser.parseMetadata(mdfile);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        
        Set<ArtefactId> artefacts = new LinkedHashSet<>();
        for (Entry<String, Long> entry : metadata.getEntries().entrySet()) {
            Path path = Paths.get(entry.getKey());
            Long checksum = entry.getValue();
            artefacts.add(ArtefactId.create(path, checksum));
        }
        
        return new PatchSet(patchId, artefacts);
    }

    @Override
    public PatchSet getLatestPatch() {
        
        List<PatchId> pids = queryAppliedPatches();
        if (pids.isEmpty()) 
            return null;
        
        PatchId latestId = pids.get(pids.size() - 1);
        return getAppliedPatchSet(latestId);
    }

    @Override
    public PatchSet applySmartPatch(SmartPatch smartPatch) throws IOException {
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");
        
        if (smartPatch.getRemoveSet().isEmpty() && smartPatch.getReplaceSet().isEmpty() && smartPatch.getAddSet().isEmpty()) {
            LOG.warn("Nothing to do on empty smart patch: {}", smartPatch);
            return null;
        }
        
        // Remove all files in the remove set
        for (ArtefactId artefactId : smartPatch.getRemoveSet()) {
            Path path = getServerHome().resolve(artefactId.getPath());
            IllegalStateAssertion.assertTrue(path.toFile().exists(), "Path does not exist: " + path);
            Files.delete(path);
        }
        
        Set<ArtefactId> artefacts = new HashSet<>();
        
        // Handle replace and add sets
        File patchFile = smartPatch.getPatchFile();
        ZipInputStream zip = new ZipInputStream(new FileInputStream(patchFile));
        try {
            byte[] buffer = new byte[1024];
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    Path path = Paths.get(entry.getName());
                    if (smartPatch.isReplacePath(path) || smartPatch.isAddPath(path)) {
                        File file = homePath.resolve(path).toFile();
                        if (smartPatch.isReplacePath(path)) {
                            IllegalStateAssertion.assertTrue(file.exists(), "Path does not exist: " + path);
                        }
                        file.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(file);
                        try {
                            int read = zip.read(buffer);
                            while (read > 0) {
                                fos.write(buffer, 0, read);
                                read = zip.read(buffer);
                            }
                            long checksum = entry.getCrc();
                            artefacts.add(ArtefactId.create(path, checksum));
                        } finally {
                            fos.close();
                        }
                        if (file.getName().endsWith(".sh")) {
                            file.setExecutable(true);
                        }
                    }
                }
                entry = zip.getNextEntry();
            }
        } finally {
            zip.close();
        }
        
        PatchSet result = new PatchSet(smartPatch.getPatchId(), artefacts);
        updatePatchSet(result);
        return result;
    }

    public void updatePatchSet(PatchSet patchSet) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        
        // Create the patch directory
        PatchId patchId = patchSet.getPatchId();
        File patchdir = getPatchDir(patchId).toFile();
        IllegalStateAssertion.assertFalse(patchdir.exists(), "Patch directory already exists: " + patchdir);
        patchdir.mkdirs();
        
        // Write marker file
        File markerFile = getMarkerFile();
        PrintWriter pw = new PrintWriter(new FileWriter(markerFile));
        try {
            pw.println(patchId.getCanonicalForm());
        } finally {
            IOUtils.safeClose(pw);
        }

        // Write metadata file
        File mdfile = patchdir.toPath().resolve(patchId + ".metadata").toFile();
        pw = new PrintWriter(new FileWriter(mdfile));
        try {
            
            List<String> lines = new ArrayList<>();
            Collections.sort(lines);
            for (ArtefactId entry : patchSet.getArtefacts()) {
                lines.add(entry.getPath() + " " + entry.getChecksum());
            }
            Collections.sort(lines);
            
            pw.println(Parser.VERSION_PREFIX + " " + Parser.VERSION);
            for (String line : lines) {
                pw.println(line);
            }
        } finally {
            IOUtils.safeClose(pw);
        }
    }

    private Path getWorkspace() {
        return homePath.resolve(Paths.get("fusepatch", "workspace"));
    }

    private File getMarkerFile() {
        return getWorkspace().resolve(FUSEPATCH_LATEST).toFile();
    }

    private Path getPatchDir(PatchId patchId) {
        return getWorkspace().resolve(FUSEPATCH_PREFIX + patchId.getCanonicalForm());
    }

    private Path inferServerHome() {
        String jbossHome = System.getProperty("jboss.home");
        if (jbossHome == null) {
            jbossHome = System.getProperty("jboss.home.dir");
        }
        if (jbossHome == null) {
            jbossHome = System.getenv("JBOSS_HOME");
        }
        IllegalStateAssertion.assertNotNull(jbossHome, "Cannot obtain JBOSS_HOME: " + jbossHome);
        Path homePath = Paths.get(jbossHome);
        Path standalonePath = Paths.get(jbossHome, "standalone", "configuration");
        Path modulesPath = Paths.get(jbossHome, "modules");
        IllegalStateAssertion.assertTrue(homePath.toFile().exists(), "Directory JBOSS_HOME does not exist: " + jbossHome);
        IllegalStateAssertion.assertTrue(standalonePath.toFile().exists(), "Directory JBOSS_HOME/standalone does not exist: " + standalonePath);
        IllegalStateAssertion.assertTrue(modulesPath.toFile().exists(), "Directory JBOSS_HOME/modules does not exist: " + modulesPath);
        return homePath;
    }
}
