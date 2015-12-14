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
package org.wildfly.extras.patch.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.ManagedPaths;
import org.wildfly.extras.patch.MetadataParser;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public final class WildFlyServer implements Server {

    private static final Logger LOG = LoggerFactory.getLogger(WildFlyServer.class);

    public static final String MODULE_LAYER = "fuse";
    
    private final Lock lock;
    private final Path homePath;

    public WildFlyServer(Lock lock, Path homePath) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        this.lock = lock;
        
        if (homePath == null) {
            homePath = getConfiguredHomePath();
        }
        IllegalStateAssertion.assertNotNull(homePath, "Cannot obtain JBOSS_HOME");
        IllegalStateAssertion.assertTrue(homePath.toFile().isDirectory(), "Directory JBOSS_HOME does not exist: " + homePath);
        this.homePath = homePath.toAbsolutePath();
    }

    @Override
    public Path getServerHome() {
        return homePath;
    }

    @Override
    public List<PatchId> queryAppliedPatches() {
        lock.tryLock();
        try {
            return MetadataParser.queryAvailablePatches(getWorkspace(), null, true);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ManagedPath> queryManagedPaths(String pattern) {
        lock.tryLock();
        try {
            return MetadataParser.queryManagedPaths(getWorkspace(), pattern);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch getPatch(String prefix) {
        IllegalArgumentAssertion.assertNotNull(prefix, "prefix");
        lock.tryLock();
        try {
            List<PatchId> list = MetadataParser.queryAvailablePatches(getWorkspace(), prefix, true);
            return list.isEmpty() ? null : getPatch(list.get(0));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> getAuditLog() {
        lock.tryLock();
        try {
            return MetadataParser.readAuditLog(getWorkspace());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch getPatch(PatchId patchId) {
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        lock.tryLock();
        try {
            return MetadataParser.readPatch(getWorkspace(), patchId);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Patch applySmartPatch(SmartPatch smartPatch, boolean force) throws IOException {
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");
        lock.tryLock();
        try {
            // Do nothing on empty smart patch
            if (smartPatch.getRecords().isEmpty()) {
                LOG.warn("Patch {} has already been applied", smartPatch.getPatchId());
                return null;
            }

            // Verify dependencies
            List<PatchId> appliedPatches = queryAppliedPatches();
            List<PatchId> unsatisfied = new ArrayList<>();
            for (PatchId depId : smartPatch.getMetadata().getDependencies()) {
                if (!appliedPatches.contains(depId)) {
                    unsatisfied.add(depId);
                }
            }
            PatchAssertion.assertTrue(unsatisfied.isEmpty(), "Unsatisfied dependencies: " + unsatisfied);

            PatchId patchId = smartPatch.getPatchId();
            Patch serverSet = getPatch(patchId.getName());
            PatchId serverId = serverSet != null ? serverSet.getPatchId() : null;

            // Get the latest applied records
            Map<Path, Record> serverRecords = new HashMap<>();
            if (serverSet != null) {
                for (Record rec : serverSet.getRecords()) {
                    serverRecords.put(rec.getPath(), rec);
                }
            }

            // Remove all records in the remove set
            for (Record rec : smartPatch.getRemoveSet()) {
                Path path = homePath.resolve(rec.getPath());
                if (!path.toFile().exists()) {
                    LOG.warn("Attempt to delete a non existing file: {}", rec.getPath());
                }
                serverRecords.remove(rec.getPath());
            }

            // Replace records in the replace set
            for (Record rec : smartPatch.getReplaceSet()) {
                Path path = homePath.resolve(rec.getPath());
                String filename = path.getFileName().toString();
                if (!path.toFile().exists()) {
                    LOG.warn("Attempt to replace a non existing file: {}", rec.getPath());
                } else if (filename.endsWith(".xml") || filename.endsWith(".properties")) {
                    Record exprec = serverRecords.get(rec.getPath());
                    Long expcheck = exprec != null ? exprec.getChecksum() : 0L;
                    Long wasCheck = IOUtils.getCRC32(path);
                    if (!expcheck.equals(wasCheck)) {
                        PatchAssertion.assertTrue(force, "Attempt to override an already modified file " + rec.getPath());
                        LOG.warn("Overriding an already modified file: {}", rec.getPath());
                    }
                }
                serverRecords.put(rec.getPath(), rec);
            }

            // Add records in the add set
            for (Record rec : smartPatch.getAddSet()) {
                Path path = homePath.resolve(rec.getPath());
                if (path.toFile().exists()) {
                    Long expcheck = rec.getChecksum();
                    Long wasCheck = IOUtils.getCRC32(path);
                    if (!expcheck.equals(wasCheck)) {
                        PatchAssertion.assertTrue(force, "Attempt to add an already existing file " + rec.getPath());
                        LOG.warn("Overriding an already existing file: {}", rec.getPath());
                    }
                }
                serverRecords.put(rec.getPath(), rec);
            }

            // Update managed paths
            ManagedPaths managedPaths = MetadataParser.readManagedPaths(getWorkspace());
            managedPaths.updatePaths(homePath, smartPatch, Action.ADD, Action.UPD);
            
            // Update server files
            updateServerFiles(smartPatch, managedPaths);
            
            // Write managed paths
            managedPaths.updatePaths(homePath, smartPatch, Action.DEL);
            MetadataParser.writeManagedPaths(getWorkspace(), managedPaths);

            Patch result;

            // Update server side metadata
            if (!smartPatch.isUninstall()) {

                // Remove higer versions on downgrade
                if (serverId != null && serverId.compareTo(patchId) > 0) {
                    for (PatchId auxId : MetadataParser.queryAvailablePatches(getWorkspace(), patchId.getName(), false)) {
                        if (auxId.compareTo(patchId) > 0) {
                            File packageDir = MetadataParser.getMetadataDirectory(getWorkspace(), auxId).getParentFile();
                            IOUtils.rmdirs(packageDir.toPath());
                        }
                    }
                }

                Set<Record> records = new HashSet<>();
                for (Record rec : serverRecords.values()) {
                    records.add(Record.create(rec.getPath(), rec.getChecksum()));
                }
                result = Patch.create(smartPatch.getMetadata(), records);
                MetadataParser.writePatch(getWorkspace(), result);
            }

            // Remove metadata on uninstall
            else {
                result = Patch.create(smartPatch.getMetadata(), smartPatch.getRecords());
                File packageDir = MetadataParser.getMetadataDirectory(getWorkspace(), patchId).getParentFile();
                IOUtils.rmdirs(packageDir.toPath());
            }

            // Write audit log
            String message;
            if (serverId == null) {
                message = "Installed " + patchId;
            } else {
                if (serverId.compareTo(patchId) < 0) {
                    message = "Upgraded from " + serverId + " to " + patchId;
                } else if (serverId.compareTo(patchId) == 0) {
                    if (smartPatch.isUninstall()) {
                        message = "Uninstalled " + patchId;
                    } else {
                        message = "Reinstalled " + patchId;
                    }
                } else {
                    message = "Downgraded from " + serverId + " to " + patchId;
                }
            }
            MetadataParser.writeAuditLog(getWorkspace(), message, smartPatch);

            // Write the log message
            LOG.info(message);

            // Run post install commands
            if (!smartPatch.isUninstall()) {
                Runtime runtime = Runtime.getRuntime();
                File procdir = homePath.toFile();
                for (String cmd : smartPatch.getMetadata().getPostCommands()) {
                    LOG.info("Run: {}", cmd);
                    String[] cmdarr = cmd.split("\\s") ;
                    Process proc = runtime.exec(cmdarr, null, procdir);
                    try {
                        startStreaming(proc.getInputStream(), System.out);
                        startStreaming(proc.getErrorStream(), System.err);
                        if (proc.waitFor() != 0) {
                            LOG.error("Command did not terminate normally: {}" + cmd);
                            break;
                        }
                    } catch (InterruptedException ex) {
                        // ignore
                    }
                }
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    private Thread startStreaming(final InputStream input, final OutputStream output) {
        Thread thread = new Thread("io") {
            @Override
            public void run() {
                try {
                    IOUtils.copy(input, output);
                } catch (IOException e) {
                }
            }
        };
        thread.start();
        return thread;
    }

    private void updateServerFiles(SmartPatch smartPatch, ManagedPaths managedPaths) throws IOException {

        // Verify that the zip contains all expected add/replace paths
        Set<Path> addupdPaths = new HashSet<>();
        for (Record rec : smartPatch.getAddSet()) {
            addupdPaths.add(rec.getPath());
        }
        for (Record rec : smartPatch.getReplaceSet()) {
            addupdPaths.add(rec.getPath());
        }
        if (!smartPatch.isUninstall()) {
            try (ZipInputStream zip = getZipInputStream(smartPatch)) {
                ZipEntry entry = zip.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        Path path = Paths.get(entry.getName());
                        addupdPaths.remove(path);
                    }
                    entry = zip.getNextEntry();
                }
            }
        }
        IllegalStateAssertion.assertTrue(addupdPaths.isEmpty(), "Patch file does not contain expected paths: " + addupdPaths);

        // Remove all files in the remove set
        for (Record rec : smartPatch.getRemoveSet()) {
            Path path = rec.getPath();
            removeServerFile(managedPaths, path);
        }

        // Handle replace and add sets
        if (!smartPatch.isUninstall()) {
            try (ZipInputStream zip = getZipInputStream(smartPatch)) {
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

        // Ensure Fuse layer exists
        Path modulesPath = homePath.resolve("modules");
        if (modulesPath.toFile().isDirectory()) {
            Properties props = new Properties();
            Path layersPath = modulesPath.resolve("layers.conf");
            if (layersPath.toFile().isFile()) {
                try (FileReader fr = new FileReader(layersPath.toFile())) {
                    props.load(fr);
                }
            }
            List<String> layers = new ArrayList<>();
            String value = props.getProperty("layers");
            if (value != null) {
                for (String layer : value.split(",")) {
                    layers.add(layer.trim());
                }
            }
            if (!layers.contains(MODULE_LAYER)) {
                layers.add(0, MODULE_LAYER);
                value = "";
                for (String layer : layers) {
                    value += "," + layer;
                }
                value = value.substring(1);
                props.setProperty("layers", value);
                LOG.warn("Layers config does not contain '" + MODULE_LAYER + "', writing: {}", value);
                try (FileWriter fw = new FileWriter(layersPath.toFile())) {
                    props.store(fw, "Fixed by fusepatch");
                }
            }
        }
    }

    private void removeServerFile(ManagedPaths managedPaths, Path path) throws IOException {
        
        ManagedPath managedPath = managedPaths.getManagedPath(path);
        List<PatchId> owners = managedPath.getOwners();
        if (!owners.contains(Server.SERVER_ID)) {
            Files.deleteIfExists(homePath.resolve(path));
        }
        
        // Recursively remove managed dirs that are empty 
        Path parent = path.getParent();
        if (parent != null && managedPaths.getManagedPath(parent) != null) {
            File dir = homePath.resolve(parent).toFile();
            if (dir.isDirectory() && dir.list().length == 0) {
                removeServerFile(managedPaths, parent);
            }
        }
    }

    private ZipInputStream getZipInputStream(SmartPatch smartPatch) throws IOException {
        return new ZipInputStream(smartPatch.getDataHandler().getDataSource().getInputStream());
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
        if (jbossHome == null) {
            Path currpath = Paths.get(".");
            if (currpath.resolve("jboss-modules.jar").toFile().exists()) {
                jbossHome = currpath.toAbsolutePath().toString();
            }
        }
        return jbossHome != null ? Paths.get(jbossHome) : null;
    }

    @Override
    public String toString() {
        return "WildFlyServer[home=" + homePath + "]";
    }
}
