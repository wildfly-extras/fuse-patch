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
package com.redhat.fuse.patch;

import java.io.File;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class Main {

    public static void main(String[] args) {

        Options options = new Options();
        CmdLineParser cmdParser = new CmdLineParser(options);
        try {
            cmdParser.parseArgument(args);
        } catch (CmdLineException e) {
            options.helpScreen(cmdParser);
            return;
        }

        if (options.help || options.arguments.isEmpty()) {
            options.helpScreen(cmdParser);
            return;
        }

        try {
            Parser parser = new ParserBuilder(options).build();
            File infile = options.arguments.get(0);
            if (options.isBuildRef()) {
            	parser.buildMetadata(infile);
            } else {
            	parser.buildPatch(infile);
            }
        } catch (Throwable th) {
        	th.printStackTrace(System.err);
        }
    }
}
