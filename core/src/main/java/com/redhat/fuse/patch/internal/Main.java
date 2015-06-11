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

import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.fuse.patch.PatchId;

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
        } catch (Exception rte) {
        	LOG.error("Cannot run fusepatch", rte);
        	System.err.println("Error: " + rte.getMessage());
        	Runtime.getRuntime().exit(1);
        }
    }

	private static void run(CmdLineParser cmdParser, Options options) throws IOException {
		
		PatchTool patchTool = new PatchTool();
		
		// Query the server
		if (options.queryServer) {
			patchTool.queryServer(options.serverHome);
		} 
		
		// Query the pool
		else if (options.queryPool) {
			patchTool.queryPool(options.poolUrl);
		} 
        
        // Install to server
        else if (options.patchId != null) {
            patchTool.install(options.serverHome, options.poolUrl, PatchId.fromString(options.patchId));
        } 
        
        // Update the server
        else if (options.update) {
            patchTool.update(options.serverHome, options.poolUrl);
        } 
		
		// Show help screen
		else {
            helpScreen(cmdParser);
		}
	}

	private static void helpScreen(CmdLineParser cmdParser) {
		System.err.println("fusepatch [options...]");
		cmdParser.printUsage(System.err);
	}
}
