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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.FileOptionHandler;

import com.redhat.fuse.patch.internal.VersionOptionHandler;

public final class Options {

    @Option(name = "--help", help = true)
    boolean help;

    @Option(name = "--buildref", usage = "Boolean on whether to build the patch metadata")
    boolean buildRef;

    @Option(name = "--ref", usage = "Path to the patch metadata")
    Path ref;

    @Option(name = "--version", usage = "Patch or metadata version", handler = VersionOptionHandler.class)
    Version version;
    
    @Argument(hidden = true, handler = FileOptionHandler.class)
    List<File> arguments = new ArrayList<>();
    
    boolean isBuildRef() {
		return buildRef;
	}

	Path getRef() {
		return ref;
	}

    Version getVersion() {
		return version;
	}

    void helpScreen(CmdLineParser cmdParser) {
        System.err.println("java -jar fuse-patch-parser.jar [options...] MyInput.zip");
        cmdParser.printUsage(System.err);
    }
}