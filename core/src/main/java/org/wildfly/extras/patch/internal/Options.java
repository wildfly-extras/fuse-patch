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

import java.io.File;
import java.net.URL;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

final class Options {

	@Option(name = "--help", help = true)
	boolean help;

    @Option(name = "--config", usage = "URL to the patch tool configuration")
    URL configUrl;
    
	@Option(name = "--server", usage = "Path to the target server")
	File serverHome;

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
    
    @Option(name = "--remove", forbids = { "--add" },  usage = "Remove the given patch id from the repository")
    String removeId;
    
    @Option(name = "--install", forbids = { "--update", "--uninstall" },  usage = "Install the given patch id to the server")
    String installId;
    
    @Option(name = "--update", forbids = { "--install", "--uninstall" },  usage = "Update the server for the given patch name")
    String updateName;

    @Option(name = "--uninstall", forbids = { "--install", "--update" },  usage = "Uninstall the given patch id from the server")
    String uninstallId;
    
    @Option(name = "--metadata", depends = { "--add" }, usage = "A subcommand for --add that points to a metadata descriptor")
    URL metadataUrl;
    
    @Option(name = "--add-cmd", depends = { "--add" }, usage = "A subcommand for --add that adds a post-install command")
    String addCmd;
    
    @Option(name = "--one-off", depends = { "--add" }, usage = "A subcommand for --add that names the target id for a one-off patch")
    String oneoffId;
    
    @Option(name = "--dependencies", depends = { "--add" }, handler = StringArrayOptionHandler.class, usage = "A subcommand for --add that defines patch dependencies")
    String[]  dependencies;
    
    @Option(name = "--roles", depends = { "--add" }, handler = StringArrayOptionHandler.class, usage = "A subcommand for --add that defines required roles")
    String[]  roles;
    
    @Option(name = "--force", usage = "Force an --add, --install or --update operation")
    boolean force;
}
