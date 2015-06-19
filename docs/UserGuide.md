## User Guide

Fuse-Patch is an installer and patch utility for arbitrary servers.

The patch tool interacts with a target server instance and a repository that contains the available packages.

Here we describe typical workflows and some advanced use cases

### Downloading and Installing

You can download the latest fuse-patch binary from the [releases tab](../../../releases).

This is the standalone distribution, which comes with a local repository and is not associated with a particular server instance.

```
$ bin/fusepatch.sh --help
fusepatch [options...]
 --add URL          : Add the given archive to the repository
 --add-cmd VAL      : Add a post-install command for a given patch id
 --audit-log        : Print the audit log
 --force            : Force an install/update operation
 --install VAL      : Install the given patch id to the server
 --query-repository : Query the repository for available patches
 --query-server     : Query the server for installed patches
 --repository URL   : URL to the patch repository
 --server PATH      : Path to the target server
 --update VAL       : Update the server for the given patch name
 ```
 
The standalone distribution already contains the fuse-patch wildfly package.

```
$ bin/fusepatch.sh --query-repository
fuse-patch-distro-wildfly-1.3.0
```

### Installing a package

Lets assume we work with WildFly and want to use fusepatch from within WildFly.

```
$ bin/fusepatch.sh --server=../wildfly-8.2.0.Final --update=fuse-patch-distro-wildfly
INFO  Installing fuse-patch-distro-wildfly-1.3.0
```

Now we can switch to WildFly and query the server

```
$ cd ../wildfly-8.2.0.Final
$ bin/fusepatch.sh --query-server
fuse-patch-distro-wildfly-1.3.0
```
Note, that the above uses `--update` instead of `--install`, which installs the latest available package for a given name.

###  Loading the Repository

The repository contains packages identified by symbolic-name and version. 

Lets add two versions of a given package to the repository.

```
$ bin/fusepatch.sh --add=file:foo-1.0.0.zip 
INFO  Adding foo-1.0.0

$ bin/fusepatch.sh --add=file:foo-1.1.0.zip 
INFO  Adding foo-1.1.0

$ bin/fusepatch.sh --query-repository
foo-1.1.0
foo-1.0.0
```

###  Updating the Server

The server can be updated with packages from the repository.

Lets first install the initial version `foo-1.0.0` and then update it to version `foo-1.1.0`

```
$ bin/fusepatch.sh --install=foo-1.0.0
INFO  Installing foo-1.0.0

$ bin/fusepatch.sh --update=foo
INFO  Upgrading from foo-1.0.0 to foo-1.1.0
```

The server side maintains an audit log 

```
$ bin/fusepatch.sh --audit-log

# 19-Jun-2015 12:22:04
# Installing foo-1.0.0

[content]
ADD config/propsA.properties 1684822571
ADD config/removeme.properties 1684822571
ADD lib/foo-1.0.0.jar 2509787836

# 19-Jun-2015 12:22:13
# Upgrading from foo-1.0.0 to foo-1.1.0

[content]
UPD config/propsA.properties 1329662440
DEL config/removeme.properties 1684822571
DEL lib/foo-1.0.0.jar 2509787836
ADD lib/foo-1.1.0.jar 2509787836
```

###  Downgrading the Server

The server can be reset to any given version

```
$ bin/fusepatch.sh --install=foo-1.0.0
INFO  Downgrading from foo-1.1.0 to foo-1.0.0
```

The audit log shows that the changes were reversed

```
$ bin/fusepatch.sh --audit-log

...

# 19-Jun-2015 12:26:20
# Downgrading from foo-1.1.0 to foo-1.0.0

[content]
UPD config/propsA.properties 1684822571
ADD config/removeme.properties 1684822571
ADD lib/foo-1.0.0.jar 2509787836
DEL lib/foo-1.1.0.jar 2509787836
```

### Conflicting Packages

It is guaranteed that the repository does not contain conflicting packages. Specifically, the set of paths associated with one package cannot overlap with the set of paths from another package. This allows packages to get applied to the server independently without the possibility that content from one package overrides the files from another.

Not enforcing this constraint would mean to create an ordering issue between packages.
For example: `bar-1.0.0` can only be installed after `foo-1.1.0` 

Lets test this on clean repository

```
$ bin/fusepatch.sh --add=file:foo-1.0.0.zip 
INFO  Adding foo-1.0.0

$ cp foo-1.1.0.zip bar-1.0.0.zip
$ bin/fusepatch.sh --add=file:bar-1.0.0.zip 
ERROR Path 'config/propsA.properties' already contained in: foo-1.0.0
ERROR Cannot add bar-1.0.0 because of duplicate paths in [foo-1.0.0]

$ bin/fusepatch.sh --query-repository
foo-1.0.0
```

[TODO [#24](https://github.com/wildfly-extras/fuse-patch/issues/24)] Add support for one-of patches

### Conflicting Server Paths

There are the three possible conflict scenarios 

1. An install/update operation wants to add a file that already exists 
2. An update operation wants modify a file that has been changed by the user
3. An update operation wants to remove a file that has already been removed

#### Adding a file that already exists 

By default, the install/update operation fails if the target file already exists.

```
$ mkdir config; touch config/propsA.properties

$ bin/fusepatch.sh --update=foo
Installing foo-1.0.0
Error: Attempt to add an already existing file config/propsA.properties
```

The `--force` option can be used to override the target file anyway

```
$ bin/fusepatch.sh --update=foo --force
Installing foo-1.0.0
Warning: Overriding an already existing file config/propsA.properties
```

#### Modifying a file that has been changed

By default, the update operation fails if the target file had been modified. This is checked for `*.xml` and `*.properties` extensions.

```
$ echo x >> config/propsA.properties 

$ bin/fusepatch.sh --update=foo
Upgrading from foo-1.0.0 to foo-1.1.0
Error: Attempt to override an already modified file config/propsA.properties
```

Again, the `--force` option can be used to override the target file anyway

```
[tdiesler@macbook wildfly-8.2.0.Final]$ bin/fusepatch.sh --update=foo --force
Upgrading from foo-1.0.0 to foo-1.1.0
Warning: Overriding an already modified file config/propsA.properties
```

#### Removing a file that has already been removed

In this case, we only issue a warming because the file would be deleted anyway.

```
$ rm config/removeme.properties 

$ bin/fusepatch.sh --update=foo
Upgrading from foo-1.0.0 to foo-1.1.0
Warning: Attempt to delete a non existing file config/removeme.properties
```
