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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchSet;
import org.wildfly.extras.patch.PatchSet.Action;
import org.wildfly.extras.patch.PatchSet.Record;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.Version;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;
import org.wildfly.extras.patch.utils.IllegalStateAssertion;

final class Parser {

    static Version VERSION;
    static {
        try (InputStream input = SmartPatch.class.getResourceAsStream("version.properties")) {
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            String line = br.readLine();
            while (line != null) {
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("#")) {
                    VERSION = Version.parseVersion(line);
                    break;
                }
                line = br.readLine();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    static final String VERSION_PREFIX = "# fusepatch:";
    static final String PATCHID_PREFIX = "# patch id:";
    
    static PatchSet buildPatchSetFromZip(PatchId patchId, Action action, File zipfile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(zipfile, "zipfile");
        IllegalArgumentAssertion.assertTrue(zipfile.isFile(), "Zip file does not exist: " + zipfile);
        
        Set<Record> records = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipfile))) {
            byte[] buffer = new byte[1024];
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName();
                    int read = zip.read(buffer);
                    while (read > 0) {
                        read = zip.read(buffer);
                    }
                    long crc = entry.getCrc();
                    records.add(Record.create(action, Paths.get(name), crc));
                }
                entry = zip.getNextEntry();
            }
        }
        return PatchSet.create(patchId, records);
    }

    static PatchSet readPatchSet(Path rootPath, PatchId patchId) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(patchId, "patchId");
        File metdataFile = getMetadataFile(rootPath, patchId);
        IllegalStateAssertion.assertTrue(metdataFile.exists(), "Cannot obtain metadata file: " + metdataFile);
        return readPatchSet(metdataFile);
    }

    static PatchSet readPatchSet(File metdataFile) throws IOException {
        IllegalArgumentAssertion.assertNotNull(metdataFile, "metdataFile");
        IllegalArgumentAssertion.assertTrue(metdataFile.isFile(), "Cannot find metadata file: " + metdataFile);

        Set<Record> records = new HashSet<>();
        List<String> commands = new ArrayList<>();
    	try (BufferedReader br = new BufferedReader(new FileReader(metdataFile))) {
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
                if ("[content]".equals(mode)) {
                    records.add(Record.fromString(line));
                }
                if ("[post-install-commands]".equals(mode)) {
                    commands.add(line);
                }
    			line = br.readLine();
    		} 
            return PatchSet.create(patchId, records, commands);
    	}
    }

    static List<PatchId> getAvailable(Path rootPath, final String prefix, boolean latest) {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        final Map<String, TreeSet<PatchId>> auxmap = new HashMap<>();
        if (rootPath.toFile().exists()) {
            try {
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String name = path.getFileName().toString();
                        if ((prefix == null || name.startsWith(prefix)) && name.endsWith(".metadata")) {
                            PatchId patchId = PatchId.fromFile(path.toFile());
                            TreeSet<PatchId> idset = auxmap.get(patchId.getName());
                            if (idset == null) {
                                idset = new TreeSet<>();
                                auxmap.put(patchId.getName(), idset);
                            }
                            idset.add(patchId);
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
    
    static void writeAuditLog(Path rootPath, String message, SmartPatch smartPatch) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(message, "message");
        IllegalArgumentAssertion.assertNotNull(smartPatch, "smartPatch");
        try (FileOutputStream fos = new FileOutputStream(rootPath.resolve("audit.log").toFile(), true)) {
            PrintStream pw = new PrintStream(fos);
            pw.println();
            String date = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date());
            pw.println("# " + date);
            pw.println("# " + message);
            PatchSet patchSet = PatchSet.create(smartPatch.getPatchId(), smartPatch.getRecords(), smartPatch.getPostCommands());
            writePatchSet(patchSet, fos, false);
        }
    }
    
    static List<String> readAuditLog(Path rootPath) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        List<String> lines = new ArrayList<>();
        File auditFile = rootPath.resolve("audit.log").toFile();
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
    
    static void writePatchSet(Path rootPath, PatchSet patchSet) throws IOException {
        IllegalArgumentAssertion.assertNotNull(rootPath, "rootPath");
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        File metadataFile = getMetadataFile(rootPath, patchSet.getPatchId());
        metadataFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            writePatchSet(patchSet, fos, true);
        }
    }
    
    static void writePatchSet(PatchSet patchSet, OutputStream outstream) throws IOException {
        writePatchSet(patchSet, outstream, true);
    }
    
    private static void writePatchSet(PatchSet patchSet, OutputStream outstream, boolean versions) throws IOException {
        IllegalArgumentAssertion.assertNotNull(patchSet, "patchSet");
        IllegalArgumentAssertion.assertNotNull(outstream, "outstream");
        try (PrintStream pw = new PrintStream(outstream)) {
            if (versions) {
                pw.println(VERSION_PREFIX + " " + VERSION);
                pw.println(PATCHID_PREFIX + " " + patchSet.getPatchId());
            }
            
            pw.println();
            pw.println("[content]");
            for (Record rec : patchSet.getRecords()) {
                pw.println(rec.toString());
            }
            
            List<String> commands = patchSet.getPostCommands();
            if (!commands.isEmpty()) {
                pw.println();
                pw.println("[post-install-commands]");
                for (String cmd : commands) {
                    pw.println(cmd);
                }
            }
        }
    }
    
    private static File getMetadataFile(Path rootPath, PatchId patchId) {
        return rootPath.resolve(Paths.get(patchId.getName(), patchId.getVersion().toString(), patchId + ".metadata")).toFile();
    }
}
