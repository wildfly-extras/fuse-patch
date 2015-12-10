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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

public final class MetadataParser {

    private static final String AUDIT_LOG = "audit.log";
    private static final String MANAGED_PATHS = "managed-paths.metadata";

    static final String VERSION_PREFIX = "# fusepatch:";
    static final String PATCHID_PREFIX = "# patch id:";

    public static Package buildPackageFromZip(PatchId patchId, Record.Action action, ZipInputStream zipInput) throws IOException {
        IllegalArgumentAssertion.assertNotNull(zipInput, "zipInput");

        Set<Record> records = new HashSet<>();
        byte[] buffer = new byte[1024];
        ZipEntry entry = zipInput.getNextEntry();
        while (entry != null) {
            if (!entry.isDirectory()) {
                String name = entry.getName();
                int read = zipInput.read(buffer);
                while (read > 0) {
                    read = zipInput.read(buffer);
                }
                long crc = entry.getCrc();
                records.add(Record.create(patchId, action, Paths.get(name), crc));
            }
            entry = zipInput.getNextEntry();
        }
        return Package.create(patchId, records);
    }

    public static Package readPackage(Path rootPath, PatchId patchId) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        File metadata = getMetadataFile(rootPath, patchId);
        return metadata.isFile() ? readPackage(metadata) : null;
    }

    public static List<PatchId> queryAvailablePackages(Path rootPath, final String prefix, boolean latest) {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        final Map<String, TreeSet<PatchId>> auxmap = new HashMap<>();
        if (rootPath.toFile().exists()) {
            try {
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String name = path.getFileName().toString();
                        if (!MANAGED_PATHS.equals(name) && name.endsWith(".metadata")) {
                            if (prefix == null || name.startsWith(prefix)) {
                                PatchId patchId = PatchId.fromURL(path.toUri().toURL());
                                TreeSet<PatchId> idset = auxmap.get(patchId.getName());
                                if (idset == null) {
                                    idset = new TreeSet<>();
                                    auxmap.put(patchId.getName(), idset);
                                }
                                idset.add(patchId);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        Set<PatchId> sortedSet = new TreeSet<>();
        for (TreeSet<PatchId> set : auxmap.values()) {
            if (latest) {
                sortedSet.add(set.last());
            } else {
                sortedSet.addAll(set);
            }
        }
        List<PatchId> result = new ArrayList<>(sortedSet);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public static List<ManagedPath> queryManagedPaths(Path rootPath, String pattern) {
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

    public static void writeAuditLog(Path rootPath, String message, SmartPatch smartPatch) throws IOException {
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
            PackageMetadata metadata = new PackageMetadataBuilder().patchId(patchId).postCommands(postCommands).build();
            Package patchSet = Package.create(metadata, smartPatch.getRecords());
            writePackage(patchSet, fos, false);
        }
    }

    public static List<String> readAuditLog(Path rootPath) throws IOException {
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

    public static void writePackage(Path rootPath, Package patchSet) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        File metadataFile = getMetadataFile(rootPath, patchSet.getPatchId());
        metadataFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            writePackage(patchSet, fos, true);
        }
    }

    public static File getMetadataDirectory(Path rootPath, PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString())).toFile();
    }

    public static File getMetadataFile(Path rootPath, PatchId patchId) {
        return getMetadataDirectory(rootPath, patchId).toPath().resolve(patchId + ".metadata").toFile();
    }

    public static ManagedPaths readManagedPaths(Path rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<ManagedPath> managedPaths = new ArrayList<>();
        File metadataFile = rootPath.resolve(MANAGED_PATHS).toFile();
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

    public static void writeManagedPaths(Path rootPath, ManagedPaths managedPaths) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(managedPaths, "managedPaths");
        File metadataFile = rootPath.resolve(MANAGED_PATHS).toFile();
        metadataFile.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(metadataFile))) {
            for (ManagedPath path : managedPaths.getManagedPaths()) {
                pw.println(path.toString());
            }
        }
    }

    private static void writePackage(Package patchSet, OutputStream outstream, boolean addHeader) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        IllegalArgumentAssertion.assertNotNull(outstream, "outstream");
        try (PrintStream pw = new PrintStream(outstream)) {

            if (addHeader) {
                pw.println(VERSION_PREFIX + " " + PatchTool.VERSION);
                pw.println(PATCHID_PREFIX + " " + patchSet.getPatchId());
            }

            Set<PatchId> deps = patchSet.getMetadata().getDependencies();
            if (!deps.isEmpty()) {
                pw.println();
                pw.println("[properties]");
                String spec = deps.toString();
                spec = spec.substring(1, spec.length() - 1);
                pw.println("Dependencies: " + spec);
            }

            pw.println();
            pw.println("[content]");
            for (Record rec : patchSet.getRecords()) {
                pw.println(rec.toString());
            }

            List<String> commands = patchSet.getMetadata().getPostCommands();
            if (!commands.isEmpty()) {
                pw.println();
                pw.println("[post-install-commands]");
                for (String cmd : commands) {
                    pw.println(cmd);
                }
            }
        }
    }

    private static Package readPackage(File metadataFile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metadataFile, "metadataFile");
        IllegalArgumentAssertion.assertTrue(metadataFile.isFile(), "Cannot find metadata file: " + metadataFile);

        Set<Record> records = new HashSet<>();
        List<String> commands = new ArrayList<>();
        Set<PatchId> dependencies = new LinkedHashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(metadataFile))) {
            String line = br.readLine().trim();
            IllegalStateAssertion.assertTrue(line.startsWith(VERSION_PREFIX), "Cannot obtain version info");
            line = br.readLine().trim();
            IllegalStateAssertion.assertTrue(line.startsWith(PATCHID_PREFIX), "Cannot obtain patch id");
            PatchId patchId = PatchId.fromString(line.substring(PATCHID_PREFIX.length()).trim());
            String mode = null;
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
                if ("[properties]".equals(mode)) {
                    String[] toks = line.split(":");
                    IllegalStateAssertion.assertEquals(2, toks.length, "Illegal property spec: " + line);
                    String name = toks[0].trim();
                    String value = toks[1].trim();
                    if ("Dependencies".equals(name)) {
                        IllegalStateAssertion.assertTrue(dependencies.isEmpty(), "Dependencies already defined" + line);
                        for (String tok : value.split(",")) {
                            dependencies.add(PatchId.fromString(tok.trim()));
                        }
                    }
                }
                if ("[content]".equals(mode)) {
                    records.add(Record.fromString(line));
                }
                if ("[post-install-commands]".equals(mode)) {
                    commands.add(line);
                }
                line = br.readLine();
            }
            PackageMetadata metadata = new PackageMetadataBuilder().patchId(patchId).dependencies(dependencies).postCommands(commands).build();
            return Package.create(metadata, records);
        }
    }
}
