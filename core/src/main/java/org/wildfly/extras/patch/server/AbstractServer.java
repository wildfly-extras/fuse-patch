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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.ManagedPaths;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.MetadataParser;
import org.wildfly.extras.patch.utils.IOUtils;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;
import org.wildfly.extras.patch.utils.PatchAssertion;

public abstract class AbstractServer implements Server {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractServer.class);
    
    private static final String AUDIT_LOG = "audit.log";
    
    private final Lock lock;
    private final Path homePath;

    public AbstractServer(Lock lock, Path homePath) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        IllegalArgumentAssertion.assertNotNull(homePath, "homePath");
        this.homePath = homePath.toAbsolutePath();
        this.lock = lock;
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
            return queryManagedPaths(getWorkspace(), pattern);
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
            return readAuditLog(getWorkspace());
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

            // Write log message
            String message;
            if (serverId == null) {
                message = "Install " + patchId;
            } else {
                if (serverId.compareTo(patchId) < 0) {
                    message = "Upgrade from " + serverId + " to " + patchId;
                } else if (serverId.compareTo(patchId) == 0) {
                    if (smartPatch.isUninstall()) {
                        message = "Uninstall " + patchId;
                    } else {
                        message = "Reinstall " + patchId;
                    }
                } else {
                    message = "Downgrade from " + serverId + " to " + patchId;
                }
            }
            LOG.info(message);

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
            ManagedPaths managedPaths = readManagedPaths(getWorkspace());
            managedPaths.updatePaths(homePath, smartPatch, Action.ADD, Action.UPD);

            // Update server files
            updateServerFiles(smartPatch, managedPaths);

            // Write managed paths
            managedPaths.updatePaths(homePath, smartPatch, Action.DEL);
            writeManagedPaths(getWorkspace(), managedPaths);

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

            // Write Audit log
            writeAuditLog(getWorkspace(), message, smartPatch);

            // Run post install commands
            if (!smartPatch.isUninstall()) {
                Runtime runtime = Runtime.getRuntime();
                File procdir = homePath.toFile();
                for (String cmd : smartPatch.getMetadata().getPostCommands()) {
                    LOG.info("Run: {}", cmd);
                    String[] cmdarr = cmd.split("\\s");
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

    protected void updateServerFiles(SmartPatch smartPatch, ManagedPaths managedPaths) throws IOException {

        File tmpFile = Files.createTempFile(getWorkspace(), "smartpatch", ".zip").toFile();

        try {

            // Verify that the zip contains all expected add/replace paths
            Set<Path> addupdPaths = new HashSet<>();
            for (Record rec : smartPatch.getAddSet()) {
                addupdPaths.add(rec.getPath());
            }
            for (Record rec : smartPatch.getReplaceSet()) {
                addupdPaths.add(rec.getPath());
            }
            if (!smartPatch.isUninstall()) {
                try (FileOutputStream output = new FileOutputStream(tmpFile)) {
                    InputStream input = smartPatch.getDataHandler().getInputStream();
                    IOUtils.copy(input, output);
                }
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(tmpFile))) {
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
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(tmpFile))) {
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
        } finally {
            tmpFile.delete();
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

    private Path getWorkspace() {
        Path path = homePath.resolve(Paths.get("fusepatch", "workspace"));
        path.toFile().mkdirs();
        return path;
    }

    private List<ManagedPath> queryManagedPaths(Path rootPath, String pattern) {
        try {
            List<ManagedPath> result = new ArrayList<>();
            ManagedPaths mpaths = readManagedPaths(rootPath);
            for (ManagedPath aux : mpaths.getManagedPaths()) {
                String path = aux.getPath().toString();
                if (pattern == null || path.startsWith(pattern)) {
                    result.add(aux);
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ManagedPaths readManagedPaths(Path rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<ManagedPath> managedPaths = new ArrayList<>();
        File metadataFile = rootPath.resolve(MetadataParser.MANAGED_PATHS).toFile();
        if (metadataFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(metadataFile))) {
                String line = br.readLine();
                while (line != null) {
                    managedPaths.add(ManagedPath.fromString(line));
                    line = br.readLine();
                }
            }
        }
        return new ManagedPaths(managedPaths);
    }

    private void writeManagedPaths(Path rootPath, ManagedPaths managedPaths) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(managedPaths, "managedPaths");
        File metadataFile = rootPath.resolve(MetadataParser.MANAGED_PATHS).toFile();
        metadataFile.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(metadataFile))) {
            for (ManagedPath path : managedPaths.getManagedPaths()) {
                pw.println(path.toString());
            }
        }
    }

    private void writeAuditLog(Path rootPath, String message, SmartPatch smartPatch) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(message, "message");
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");
        try (FileOutputStream fos = new FileOutputStream(rootPath.resolve(AUDIT_LOG).toFile(), true)) {
            PrintStream pw = new PrintStream(fos);
            pw.println();
            String date = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date());
            pw.println("# " + date);
            pw.println("# " + message);
            PatchId patchId = smartPatch.getPatchId();
            List<String> postCommands = smartPatch.getMetadata().getPostCommands();
            PatchMetadata metadata = new PatchMetadataBuilder().patchId(patchId).postCommands(postCommands).build();
            Patch patch = Patch.create(metadata, smartPatch.getRecords());
            MetadataParser.writePatch(patch, fos, false);
        }
    }

    private List<String> readAuditLog(Path rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<String> lines = new ArrayList<>();
        File auditFile = rootPath.resolve(AUDIT_LOG).toFile();
        if (auditFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(auditFile))) {
                String line = br.readLine();
                while (line != null) {
                    lines.add(line);
                    line = br.readLine();
                }
            }
        }
        return Collections.unmodifiableList(lines);
    }
}
