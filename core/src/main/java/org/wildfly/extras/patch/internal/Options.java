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

import java.net.URL;
import java.nio.file.Path;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

final class Options {

	@Option(name = "--help", help = true)
	boolean help;

	@Option(name = "--server", usage = "Path to the target server")
	Path serverHome;

	@Option(name = "--repository", usage = "URL to the patch repository")
	URL repositoryUrl;

    @Option(name = "--query-repository", usage = "Query the repository for available patches")
    boolean queryRepository;
    
    @Option(name = "--query-server", usage = "Query the server for installed patches")
    boolean queryServer;

    @Option(name = "--query-server-paths", usage = "Query managed server paths")
    String queryServerPaths;

    @Option(name = "--audit-log", usage = "Print the audit log")
    boolean auditLog;

    @Option(name = "--add", forbids = { "--remove" },  usage = "Add the given archive to the repository")
    URL addUrl;
    
    @Option(name = "--add-cmd", handler = StringArrayOptionHandler.class, usage = "Add a post-install command for a given patch id")
    String[] addCmd;
    
    @Option(name = "--one-off", depends = { "--add" }, usage = "A subcommand for --add that names the target id for a one-off patch")
    String oneoffId;
    
    @Option(name = "--dependencies", depends = { "--add" }, usage = "A subcommand for --add that defines an array of dependencies")
    String[]  dependencies;
    
    @Option(name = "--remove", forbids = { "--add" },  usage = "Remove the given patch id from the repository")
    String removeId;
    
    @Option(name = "--install", forbids = { "--update" },  usage = "Install the given patch id to the server")
    String installId;
    
    @Option(name = "--update", forbids = { "--install" },  usage = "Update the server for the given patch name")
    String updateName;

    @Option(name = "--force", usage = "Force an --add, --install or --update operation")
    boolean force;
}
