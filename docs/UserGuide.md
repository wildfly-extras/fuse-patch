## User Guide

Fuse-Patch is an installer and patch utility for arbitrary servers.

The patch tool interacts with a target server instance and a repository that contains the available patches.

Here we describe typical workflows and some advanced use cases

### Contents

1. [Download and Install](UserGuide.md#download-and-install)
2. [Installing a Patch](UserGuide.md#installing-a-patch)
3. [Loading the Repository](UserGuide.md#loading-the-repository)
4. [Upgrading a Patch](UserGuide.md#upgrading-a-patch)
5. [Downgrading a Patch](UserGuide.md#downgrading-a-patch)
6. [Uninstalling a Patch](UserGuide.md#uninstalling-a-patch)
7. [Conflicting Patches](UserGuide.md#conflicting-patches)
8. [Conflicting Server Paths](UserGuide.md#conflicting-server-paths)
9. [Running Post-Install Commands](UserGuide.md#running-post-install-commands)
10. [Support for One-Off Patches](UserGuide.md#support-for-one-off-patches)
11. [Support for Patch Dependencies](UserGuide.md#support-for-patch-dependencies)

### Download and Install

You can download the latest fuse-patch binary from the [releases tab](https://github.com/wildfly-extras/fuse-patch/releases).

This is the standalone distribution, which comes with a local repository and is not associated with a particular server instance.

```
$ bin/fusepatch.sh --help
fusepatch [options...]
 --add URL                : Add the given archive to the repository
 --add-cmd VAL            : A subcommand for --add that adds a post-install command
 --audit-log              : Print the audit log
 --config URL             : URL to the patch tool configuration
 --dependencies STRING[]  : A subcommand for --add that defines patch dependencies
 --force                  : Force an --add, --install or --update operation
 --install VAL            : Install the given patch id to the server
 --metadata URL           : A subcommand for --add that points to a metadata descriptor
 --one-off VAL            : A subcommand for --add that names the target id for a one-off patch
 --query-repository       : Query the repository for available patches
 --query-server           : Query the server for installed patches
 --query-server-paths VAL : Query managed server paths
 --remove VAL             : Remove the given patch id from the repository
 --repository URL         : URL to the patch repository
 --roles STRING[]         : A subcommand for --add that defines required roles
 --server PATH            : Path to the target server
 --uninstall VAL          : Uninstall the given patch id from the server
 --update VAL             : Update the server for the given patch name
 ```

The standalone distribution already contains the fuse-patch wildfly patch.

```
$ bin/fusepatch.sh --query-repository
fuse-patch-distro-wildfly-2.0.0
```

### Client Configuration

Conceptually Fuse-Patch consists of a thin client located alongside the server instance it operates on and a remote repository that the client connects to.
How the client connects to the repository is determined by the repository URL.

Currently Fuse-Patch supports three flavours of repository

* Local file based repository
* JAX-WS endpoint that implements the repository interface
* Aether based repository that delegates to a Maven repository

The repository client can be configured by a config URL that points to a [properties](https://github.com/wildfly-extras/fuse-patch/blob/master/core/src/main/java/org/wildfly/extras/patch/Configuration.java) file.
The webservice enpoint is expected to be hosted on an application server, which provides a user/role mapping. Access to repository contend can such be secured by roles on an individual bassis.   

### Installing a Patch

Lets assume we work with WildFly and want to use fusepatch with WildFly.

```
$ bin/fusepatch.sh --server ../wildfly-8.2.0.Final --update fuse-patch-distro-wildfly
Installed fuse-patch-distro-wildfly-2.0.0
```

Note, that the above uses `--update` instead of `--install`, which installs the latest available patch for a given name.

Now we can switch to WildFly and query the server for installed patches

```
$ cd ../wildfly-8.2.0.Final
$ bin/fusepatch.sh --query-server
fuse-patch-distro-wildfly-2.0.0
```

or managed paths

```
$ cd ../wildfly-8.2.0.Final
$ bin/fusepatch.sh --query-server-paths modules
modules/layers.conf [fuse-patch-distro-wildfly-2.0.0]
modules/system/layers/fuse/org/wildfly/extras/patch/main/args4j-2.0.31.jar [fuse-patch-distro-wildfly-2.0.0]
modules/system/layers/fuse/org/wildfly/extras/patch/main/fuse-patch-core-1.4.1-SNAPSHOT.jar [fuse-patch-distro-wildfly-2.0.0]
modules/system/layers/fuse/org/wildfly/extras/patch/main/module.xml [fuse-patch-distro-wildfly-2.0.0]
```

###  Loading the Repository

The repository contains patches identified by symbolic-name and version.

Lets add three versions of a given patch to the repository.

```
$ bin/fusepatch.sh --add file:foo-1.0.0.zip
Added foo-1.0.0

$ bin/fusepatch.sh --add file:foo-1.1.0.zip
Added foo-1.1.0

$ bin/fusepatch.sh --metadata file://.../foo-1.2.0-metadata.xml --add file:foo-1.2.0.zip
Added foo-1.2.0

$ bin/fusepatch.sh --query-repository
foo-1.2.0
foo-1.1.0
foo-1.0.0
```

Patches may have additional metadata associated with it.

```xml
    <package>
        <patchId>foo-1.0.0.SP1</patchId>
        <oneoffId>foo-1.0.0</oneoffId>
        <roles>
            <role>foo</role>
        </roles>
        <dependencies>
            <patchId>aaa-0.0.0</patchId>
        </dependencies>
        <post-commands>
            <command>echo done</command>
        </post-commands>
    </package>
```

Unwanted patches can be removed from the repository.

```
$ bin/fusepatch.sh --remove foo-1.0.0
Removed foo-1.0.0
```

###  Upgrading a Patch

The server can be updated with patches from the repository.

Lets first install the initial version `foo-1.0.0` and then update it to version `foo-1.1.0`

```
$ bin/fusepatch.sh --install foo-1.0.0
Installed foo-1.0.0

$ bin/fusepatch.sh --update foo
Upgraded from foo-1.0.0 to foo-1.1.0
```

The server side maintains an audit log

```
$ bin/fusepatch.sh --audit-log

# 19-Jun-2015 12:22:04
# Installed foo-1.0.0

[content]
ADD config/propsA.properties 1684822571
ADD config/remove-me.properties 1684822571
ADD lib/foo-1.0.0.jar 2509787836

# 19-Jun-2015 12:22:13
# Upgrading from foo-1.0.0 to foo-1.1.0

[content]
UPD config/propsA.properties 1329662440
DEL config/remove-me.properties 1684822571
DEL lib/foo-1.0.0.jar 2509787836
ADD lib/foo-1.1.0.jar 2509787836
```

###  Downgrading a Patch

The server can be reset to any given version

```
$ bin/fusepatch.sh --install foo-1.0.0
Downgraded from foo-1.1.0 to foo-1.0.0
```

The audit log shows that the changes were reversed

```
$ bin/fusepatch.sh --audit-log

...

# 19-Jun-2015 12:26:20
# Downgrading from foo-1.1.0 to foo-1.0.0

[content]
UPD config/propsA.properties 1684822571
ADD config/remove-me.properties 1684822571
ADD lib/foo-1.0.0.jar 2509787836
DEL lib/foo-1.1.0.jar 2509787836
```

###  Uninstalling a Patch

Patches can be unsinstalled from the server.

```
$ bin/fusepatch.sh --uninstall foo-1.0.0
Uninstalled foo-1.0.0
```

The audit log shows that all was removed

```
$ bin/fusepatch.sh --audit-log

...

# 06-Aug-2015 15:20:11
# Uninstalled foo-1.0.0

[content]
DEL config/propsA.properties 1396661911
DEL config/propsB.properties 427912505
DEL config/remove-me.properties 1396661911
DEL lib/foo-1.0.0.jar 3303198020
```

### Conflicting Patches

It is guaranteed that the repository does not contain conflicting patches. Specifically, the set of paths associated with one patch cannot overlap with the set of paths from another patch. This allows patches to get applied to the server independently without the possibility that content from one patch overrides the files from another.

Not enforcing this constraint would mean to create an ordering issue between patches.
For example: `bar-1.0.0` can only be installed after `foo-1.1.0`

Lets test this on clean repository

```
$ bin/fusepatch.sh --add file:foo-1.0.0.zip
Adding foo-1.0.0

$ cp foo-1.1.0.zip bar-1.0.0.zip
$ bin/fusepatch.sh --add file:bar-1.0.0.zip
ERROR Path 'config/propsA.properties' already contained in: foo-1.0.0
ERROR Cannot add bar-1.0.0 because of duplicate paths in [foo-1.0.0]

$ bin/fusepatch.sh --query-repository
foo-1.0.0
```

### Conflicting Server Paths

There are the three possible conflict scenarios

1. An install/update operation wants to add a file that already exists
2. An update operation wants modify a file that has been changed by the user
3. An update operation wants to remove a file that has already been removed

#### Adding a file that already exists

By default, the install/update operation fails if the target file already exists.

```
$ mkdir config; touch config/propsA.properties

$ bin/fusepatch.sh --update foo
Error: Attempt to add an already existing file config/propsA.properties
```

The `--force` option can be used to override the target file anyway

```
$ bin/fusepatch.sh --update foo --force
Warning: Overriding an already existing file config/propsA.properties
Installed foo-1.0.0
```

#### Modifying a file that has been changed

By default, the update operation fails if the target file had been modified. This is checked for `*.xml` and `*.properties` extensions.

```
$ echo x >> config/propsA.properties

$ bin/fusepatch.sh --update foo
Error: Attempt to override an already modified file config/propsA.properties
```

Again, the `--force` option can be used to override the target file anyway

```
$ bin/fusepatch.sh --update foo --force
Warning: Overriding an already modified file config/propsA.properties
Upgraded from foo-1.0.0 to foo-1.1.0
```

#### Removing a file that has already been removed

In this case, we only issue a warming because the file would be deleted anyway.

```
$ rm config/remove-me.properties

$ bin/fusepatch.sh --update foo
Warning: Attempt to delete a non existing file config/remove-me.properties
Upgraded from foo-1.0.0 to foo-1.1.0
```

### Running Post-Install Commands

Post install commands can be associated with a patch when it is added to the repository

```
$ bin/fusepatch.sh --add file:foo-1.0.0.zip --add-cmd "echo hello world"
Added foo-1.0.0
Added post install command to foo-1.0.0
```

Multiple post install commads can be added later like this

```
$ bin/fusepatch.sh --add-cmd foo-1.0.0 "echo hello new world"
Added post install command to foo-1.0.0
```

Commands are then executed on install or update

```
$ bin/fusepatch.sh --install foo-1.0.0
Installed foo-1.0.0
Run: echo hello world
hello world
Run: echo hello new world
hello new world
```

### Support for One-Off Patches

Already existing patches can be patched by "one-off" patches. A one-off patch does not contain the full set of paths that an ordinary patch contains. Instead, it contains a set of files that need to get patched in an already existing patch. A one-off patch cannot remove files on the target server.

```
$ bin/fusepatch.sh --add file:foo-1.0.0.zip
Added foo-1.0.0

$ bin/fusepatch.sh --add file:foo-1.0.0.SP1.zip --one-off foo-1.0.0
Added foo-1.0.0.SP1 patching foo-1.0.0

$ bin/fusepatch.sh --install foo-1.0.0
Installed foo-1.0.0

$ bin/fusepatch.sh --update foo
Upgraded from foo-1.0.0 to foo-1.0.0.SP1
```

### Support for Patch dependencies

A patch can define dependencies on other patches.

```
$ bin/fusepatch.sh --add file:foo-1.0.0.zip
Added foo-1.0.0

$ bin/fusepatch.sh --add file:foo-1.1.0.zip --depends foo-1.0.0
Added foo-1.1.0 with dependencies on [foo-1.0.0]

$ bin/fusepatch.sh --install foo-1.1.0
Error: Unsatisfied dependencies: [foo-1.0.0]

$ bin/fusepatch.sh --install foo-1.0.0
Installed foo-1.0.0

$ bin/fusepatch.sh --install foo-1.1.0
Upgraded from foo-1.0.0 to foo-1.1.0
```
