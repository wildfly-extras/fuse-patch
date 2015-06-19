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

import java.io.IOException;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);
	
    public static void main(String[] args) {

        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            helpScreen(parser);
            return;
        }

        try {
        	run(parser, options);
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        	Runtime.getRuntime().exit(1);
        }
    }

	private static void run(CmdLineParser cmdParser, Options options) throws IOException {
		
	    boolean opfound = false;
	    
		// Query the server
		if (options.queryServer) {
	        PatchTool patchTool = new PatchToolBuilder().serverPath(options.serverHome).build();
		    printPatches(patchTool.queryServer());
		    opfound = true;
		} 
		
        // Query the repository
        if (options.queryRepository) {
            PatchTool patchTool = new PatchToolBuilder().repositoryUrl(options.repositoryUrl).build();
            printPatches(patchTool.queryRepository());
            opfound = true;
        } 
        
        // Print the audit log
        if (options.auditLog) {
            PatchTool patchTool = new PatchToolBuilder().serverPath(options.serverHome).build();
            printLines(patchTool.getAuditLog());
            opfound = true;
        } 
        
        // Add to repository
        if (options.addUrl != null) {
            PatchTool patchTool = new PatchToolBuilder().repositoryUrl(options.repositoryUrl).build();
            patchTool.add(options.addUrl);
            opfound = true;
        }
        
        // Add post install command
        if (options.addCmd != null) {
            PatchTool patchTool = new PatchToolBuilder().repositoryUrl(options.repositoryUrl).build();
            int index = options.addCmd.indexOf(":");
            PatchId patchId = index > 0 ? PatchId.fromString(options.addCmd.substring(0, index)) : null;
            String cmd = index > 0 ? options.addCmd.substring(index + 1) : options.addCmd;
            patchTool.addPostCommand(patchId, cmd);
            opfound = true;
        }
        
        // Install to server
        if (options.installId != null) {
            PatchTool patchTool = new PatchToolBuilder().serverPath(options.serverHome).repositoryUrl(options.repositoryUrl).build();
            patchTool.install(PatchId.fromString(options.installId));
            opfound = true;
        }
        
        // Update the server
        if (options.updateName != null) {
            PatchTool patchTool = new PatchToolBuilder().serverPath(options.serverHome).repositoryUrl(options.repositoryUrl).build();
            patchTool.update(options.updateName);
            opfound = true;
        } 
        
		// Show help screen
		if (!opfound) {
            helpScreen(cmdParser);
		}
	}

	private static void printLines(List<String> lines) {
	    for (String line : lines) {
	        System.out.println(line);
	    }
    }

    private static void helpScreen(CmdLineParser cmdParser) {
		System.err.println("fusepatch [options...]");
		cmdParser.printUsage(System.err);
	}

    private static void printPatches(List<PatchId> patches) {
        for (PatchId patchId : patches) {
            System.out.println(patchId.toString());
        }
    }
}
