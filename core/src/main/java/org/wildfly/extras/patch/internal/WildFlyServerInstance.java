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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchSet.Record;
import org.wildfly.extras.patch.ServerInstance;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public final class WildFlyServerInstance implements ServerInstance {

    private static final Logger LOG = LoggerFactory.getLogger(WildFlyServerInstance.class);

    private final Path homePath;

    public WildFlyServerInstance(Path homePath) {
        if (homePath == null) {
            homePath = getConfiguredHomePath();
        }
        IllegalStateAssertion.assertNotNull(homePath, "Cannot obtain JBOSS_HOME");
        IllegalStateAssertion.assertTrue(homePath.toFile().isDirectory(), "Directory JBOSS_HOME does not exist: " + homePath);
        this.homePath = homePath.toAbsolutePath();
    }

    @Override
    public Path getDefaultRepositoryPath() {
        return homePath.resolve(Paths.get("fusepatch", "repository"));
    }
    
    @Override
    public Path getServerHome() {
        return homePath;
    }

    @Override
    public List<PatchId> queryAppliedPatches() {
        return Parser.getAvailable(getWorkspace(), null, true);
    }

    @Override
    public PatchId getLatestApplied(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        List<PatchId> list = Parser.getAvailable(getWorkspace(), prefix, true);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<String> getAuditLog() {
        try {
            return Parser.readAuditLog(getWorkspace());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public PatchSet getPatchSet(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        try {
            return Parser.readPatchSet(getWorkspace(), patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public PatchSet applySmartPatch(SmartPatch smartPatch, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");

        // Do nothing on empty smart patch
        if (smartPatch.getRecords().isEmpty()) {
            PatchLogger.warn("Patch " + smartPatch.getPatchId() + " has already been applied");
            return null;
        }

        PatchId patchId = smartPatch.getPatchId();
        PatchId serverId = getLatestApplied(patchId.getName());
        
        // Get the latest applied records
        Map<Path, Record> serverRecords = new HashMap<>();
        if (serverId != null) {
            PatchSet patchSet = getPatchSet(serverId);
            for (Record rec : patchSet.getRecords()) {
                serverRecords.put(rec.getPath(), rec);
            }
        }
        
        // Remove all records in the remove set
        for (Record rec : smartPatch.getRemoveSet()) {
            Path path = getServerHome().resolve(rec.getPath());
            if (!path.toFile().exists()) {
                PatchLogger.warn("Attempt to delete a non existing file " + rec.getPath());
            }
            serverRecords.remove(rec.getPath());
        }
        
        // Replace records in the replace set
        for (Record rec : smartPatch.getReplaceSet()) {
            Path path = getServerHome().resolve(rec.getPath());
            String filename = path.getFileName().toString();
            if (!path.toFile().exists()) {
                PatchLogger.warn("Attempt to replace a non existing file " + rec.getPath());
            } else if (filename.endsWith(".xml") || filename.endsWith(".properties")) {
                Record exprec = serverRecords.get(rec.getPath());
                Long expcheck = exprec != null ? exprec.getChecksum() : 0L;
                Long wasCheck = IOUtils.getCRC32(path);
                if (!expcheck.equals(wasCheck)) {
                    PatchAssertion.assertTrue(force, "Attempt to override an already modified file " + rec.getPath());
                    PatchLogger.warn("Overriding an already modified file " + rec.getPath());
                }
            }
            serverRecords.put(rec.getPath(), rec);
        }

        // Add records in the add set
        for (Record rec : smartPatch.getAddSet()) {
            Path path = getServerHome().resolve(rec.getPath());
            if (path.toFile().exists()) {
                PatchAssertion.assertTrue(force, "Attempt to add an already existing file " + rec.getPath());
                PatchLogger.warn("Overriding an already existing file " + rec.getPath());
            }
            serverRecords.put(rec.getPath(), rec);
        }

        // Update the server files
        updateServerFiles(smartPatch);

        // Update server side metadata
        Set<Record> inforecs = new HashSet<>();
        for (Record rec : serverRecords.values()) {
            inforecs.add(Record.create(rec.getPath(), rec.getChecksum()));
        }
        PatchSet infoset = PatchSet.create(patchId, inforecs);
        Parser.writePatchSet(getWorkspace(), infoset);

        // Write the log message
        final String message;
        if (serverId == null) {
            message = "Installed " + patchId;
        } else {
            if (serverId.compareTo(patchId) < 0) {
                message = "Upgraded from " + serverId + " to " + patchId;
            } else if (serverId.compareTo(patchId) == 0) {
                message = "Reinstalled " + patchId;
            } else {
                message = "Downgraded from " + serverId + " to " + patchId;
            }
        }
        PatchLogger.info(message);
        
        // Write audit log
        Parser.writeAuditLog(getWorkspace(), message, smartPatch);
       
        // Run post install commands
        Runtime runtime = Runtime.getRuntime();
        File procdir = homePath.toFile();
        for (String cmd : smartPatch.getPostCommands()) {
            PatchLogger.info("Run: " + cmd);
            String[] envarr = {};
            String[] cmdarr = cmd.split("\\s") ;
            Process proc = runtime.exec(cmdarr, envarr, procdir);
            try {
                if (proc.waitFor() != 0) {
                    LOG.error("Command did not terminate normally: " + cmd);
                    break;
                }
            } catch (InterruptedException ex) {
                // ignore
            }
        }
        
        return infoset;
    }

    private void updateServerFiles(SmartPatch smartPatch) throws IOException {
        
        // Remove all files in the remove set
        for (Record rec : smartPatch.getRemoveSet()) {
            Path path = getServerHome().resolve(rec.getPath());
            Files.deleteIfExists(path);
        }
        
        // Handle replace and add sets
        File patchFile = smartPatch.getPatchFile();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(patchFile))) {
            byte[] buffer = new byte[1024];
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    Path path = Paths.get(entry.getName());
                    if (smartPatch.isReplacePath(path) || smartPatch.isAddPath(path)) {
                        File file = homePath.resolve(path).toFile();
                        file.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            int read = zip.read(buffer);
                            while (read > 0) {
                                fos.write(buffer, 0, read);
                                read = zip.read(buffer);
                            }
                        }
                        if (file.getName().endsWith(".sh") || file.getName().endsWith(".bat")) {
                            file.setExecutable(true);
                        }
                    }
                }
                entry = zip.getNextEntry();
            }
        }
    }

    private Path getWorkspace() {
        return homePath.resolve(Paths.get("fusepatch", "workspace"));
    }

    static Path getConfiguredHomePath() {
        String jbossHome = System.getProperty("jboss.home");
        if (jbossHome == null) {
            jbossHome = System.getProperty("jboss.home.dir");
        }
        if (jbossHome == null) {
            jbossHome = System.getenv("JBOSS_HOME");
        }
        return jbossHome != null ? Paths.get(jbossHome) : null;
    }
}
