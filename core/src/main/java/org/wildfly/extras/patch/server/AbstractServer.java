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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.wildfly.extras.patch.ManagedPath;
import org.wildfly.extras.patch.ManagedPaths;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.Server;
import org.wildfly.extras.patch.SmartPatch;
import org.wildfly.extras.patch.internal.MetadataParser;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

public abstract class AbstractServer implements Server {

    private static final String AUDIT_LOG = "audit.log";
    
    protected List<ManagedPath> queryManagedPaths(Path rootPath, String pattern) {
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

    protected ManagedPaths readManagedPaths(Path rootPath) throws IOException {
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

    protected void writeManagedPaths(Path rootPath, ManagedPaths managedPaths) throws IOException {
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

    protected void writeAuditLog(Path rootPath, String message, SmartPatch smartPatch) throws IOException {
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

    protected List<String> readAuditLog(Path rootPath) throws IOException {
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
