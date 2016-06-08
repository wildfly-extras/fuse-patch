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
    private final File homePath;

    public AbstractServer(Lock lock, File homePath) {
        IllegalArgumentAssertion.assertNotNull(lock, "lock");
        IllegalArgumentAssertion.assertNotNull(homePath, "homePath");
        this.homePath = homePath.getAbsoluteFile();
        this.lock = lock;
    }
    
    @Override
    public File getServerHome() {
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
            List<PatchId> unsatisfied = new ArrayList<PatchId>();
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
            Map<File, Record> serverRecords = new HashMap<File, Record>();
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
                File path = new File(homePath, rec.getPath().getPath());
                if (!path.exists()) {
                    LOG.warn("Attempt to delete a non existing file: {}", rec.getPath());
                }
                serverRecords.remove(rec.getPath());
            }

            // Replace records in the replace set
            for (Record rec : smartPatch.getReplaceSet()) {
                File path = new File(homePath, rec.getPath().getPath());
                String filename = path.getName();
                if (!path.exists()) {
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
                File path = new File(homePath, rec.getPath().getPath());
                if (path.exists()) {
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
                            IOUtils.rmdirs(packageDir);
                        }
                    }
                }

                Set<Record> records = new HashSet<Record>();
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
                IOUtils.rmdirs(packageDir);
            }

            // Write Audit log
            writeAuditLog(getWorkspace(), message, smartPatch);

            // Run post install commands
            if (!smartPatch.isUninstall()) {
                Runtime runtime = Runtime.getRuntime();
                File procdir = homePath;
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

    @Override
    public abstract void cleanUp();

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

        File tmpFile = File.createTempFile("smartpatch", ".zip", getWorkspace());

        try {

            // Verify that the zip contains all expected add/replace paths
            Set<File> addupdPaths = new HashSet<File>();
            for (Record rec : smartPatch.getAddSet()) {
                addupdPaths.add(rec.getPath());
            }
            for (Record rec : smartPatch.getReplaceSet()) {
                addupdPaths.add(rec.getPath());
            }
            if (!smartPatch.isUninstall()) {
                FileOutputStream output = new FileOutputStream(tmpFile);
                try {
                    InputStream input = smartPatch.getDataHandler().getInputStream();
                    IOUtils.copy(input, output);
                } finally {
                    output.close();
                }
                ZipInputStream zip = new ZipInputStream(new FileInputStream(tmpFile));
                try {
                    ZipEntry entry = zip.getNextEntry();
                    while (entry != null) {
                        if (!entry.isDirectory()) {
                            File path = new File(entry.getName());
                            addupdPaths.remove(path);
                        }
                        entry = zip.getNextEntry();
                    }
                } finally {
                    zip.close();
                }
            }
            IllegalStateAssertion.assertTrue(addupdPaths.isEmpty(), "Patch file does not contain expected paths: " + addupdPaths);

            // Remove all files in the remove set
            for (Record rec : smartPatch.getRemoveSet()) {
                File path = rec.getPath();
                removeServerFile(managedPaths, path);
            }

            // Handle replace and add sets
            if (!smartPatch.isUninstall()) {
                ZipInputStream zip = new ZipInputStream(new FileInputStream(tmpFile));
                try {
                    byte[] buffer = new byte[1024];
                    ZipEntry entry = zip.getNextEntry();
                    while (entry != null) {
                        if (!entry.isDirectory()) {
                            File path = new File(entry.getName());
                            if (smartPatch.isReplacePath(path) || smartPatch.isAddPath(path)) {
                                File file = new File(homePath, path.getPath());
                                file.getParentFile().mkdirs();
                                FileOutputStream fos = new FileOutputStream(file);
                                try {
                                    int read = zip.read(buffer);
                                    while (read > 0) {
                                        fos.write(buffer, 0, read);
                                        read = zip.read(buffer);
                                    }
                                } finally {
                                    fos.close();
                                }
                                if (file.getName().endsWith(".sh") || file.getName().endsWith(".bat")) {
                                    file.setExecutable(true);
                                }
                            }
                        }
                        entry = zip.getNextEntry();
                    }
                } finally {
                    zip.close();
                }
            }
        } finally {
            tmpFile.delete();
        }
    }

    private void removeServerFile(ManagedPaths managedPaths, File path) throws IOException {

        ManagedPath managedPath = managedPaths.getManagedPath(path);
        List<PatchId> owners = managedPath.getOwners();
        if (!owners.contains(Server.SERVER_ID)) {
            File pathToRemove = new File(homePath, path.getPath());
            if (!pathToRemove.delete()) {
                // Something prevented the file being deleted, so try again on VM exit
                pathToRemove.deleteOnExit();
                LOG.warn("Deleting {} on exit", pathToRemove.getAbsoluteFile());
            }
        }

        // Recursively remove managed dirs that are empty 
        File parent = path.getParentFile();
        if (parent != null && managedPaths.getManagedPath(parent) != null) {
            File dir = new File(homePath, parent.getPath());
            if (dir.isDirectory() && dir.list().length == 0) {
                removeServerFile(managedPaths, parent);
            }
        }
    }

    private File getWorkspace() {
        File path = new File(homePath, "fusepatch" + File.separator + "workspace");
        path.mkdirs();
        return path;
    }

    private List<ManagedPath> queryManagedPaths(File rootPath, String pattern) {
        try {
            List<ManagedPath> result = new ArrayList<ManagedPath>();
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

    private ManagedPaths readManagedPaths(File rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<ManagedPath> managedPaths = new ArrayList<ManagedPath>();
        File metadataFile = new File(rootPath, MetadataParser.MANAGED_PATHS);
        if (metadataFile.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(metadataFile));
            try {
                String line = br.readLine();
                while (line != null) {
                    managedPaths.add(ManagedPath.fromString(line));
                    line = br.readLine();
                }
            } finally {
                br.close();
            }
        }
        return new ManagedPaths(managedPaths);
    }

    private void writeManagedPaths(File rootPath, ManagedPaths managedPaths) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(managedPaths, "managedPaths");
        File metadataFile = new File(rootPath, MetadataParser.MANAGED_PATHS);
        metadataFile.getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new FileWriter(metadataFile));
        try {
            for (ManagedPath path : managedPaths.getManagedPaths()) {
                pw.println(path.toString());
            }
        } finally {
            pw.close();
        }
    }

    private void writeAuditLog(File rootPath, String message, SmartPatch smartPatch) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(message, "message");
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");
        FileOutputStream fos = new FileOutputStream(new File(rootPath, AUDIT_LOG), true);
        try {
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
        } finally {
            fos.close();
        }
    }

    private List<String> readAuditLog(File rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<String> lines = new ArrayList<String>();
        File auditFile = new File(rootPath, AUDIT_LOG);
        if (auditFile.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(auditFile));
            try {
                String line = br.readLine();
                while (line != null) {
                    lines.add(line);
                    line = br.readLine();
                }
            } finally {
                br.close();
            }
        }
        return Collections.unmodifiableList(lines);
    }
}
