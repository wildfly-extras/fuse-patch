## Fuse Patch

A Fuse installer and patch utility.

The patch tool interacts with a target [server instance](core/src/main/java/com/redhat/fuse/patch/ServerInstance.java) and a [patch repository](core/src/main/java/com/redhat/fuse/patch/PatchRepository.java) that contains the available patches.

The server and the repository can be queried like this respectively

```
> fusepatch --query-server
> fusepatch --query-repository
```

The main function of the patch tool however is to install a distribution from the repository like this

```
> fusepatch --install=fuse-eap-distro-6.2.1-redhat
```

or update the server to the the latest like this

```
> fusepatch --update=fuse-eap-distro
```

The patch tool maintains audit metadata on the server, which allows to review/reproduce what has been changed and perhaps more importantly to calculate a [smart patch](core/src/main/java/com/redhat/fuse/patch/SmartPatch.java) as the required diff between the current server state and the latest available patch release in the repository.

Updating the target server involves the following steps

1. query the server for the latest installed patch
2. contact the patch repository to compute a smart patch
3. apply the smart patch, which supports file remove, replace and add operations
4. store smart patch metadata on the target server for the next query operation
