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

import java.net.URL;
import java.nio.file.Path;

import org.kohsuke.args4j.Option;

final class Options {

	@Option(name = "--help", help = true)
	boolean help;

	@Option(name = "--server", usage = "Path to the target server")
	Path serverHome;

	@Option(name = "--repository", usage = "URL to the patch repository")
	URL repositoryUrl;

	@Option(name = "--query-server", forbids = { "--query-repository", "--update", "--install" }, usage = "Query the server for installed patches")
	boolean queryServer;

	@Option(name = "--query-repository", forbids = { "--query-server", "--update", "--install" }, usage = "Query the patch repository URL for available patches")
	boolean queryRepository;

    @Option(name = "--install", forbids = { "--query-server", "--query-repository", "--update" }, usage = "Install the given patch id to the server")
    String patchId;
    
    @Option(name = "--update", forbids = { "--query-server", "--query-repository", "--install" }, usage = "Update the server")
    boolean update;
}