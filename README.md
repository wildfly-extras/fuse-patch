## Fuse Patch

A simple Fuse patch utility.

The patch tool interacts with a target [server instance](tool/src/main/java/com/redhat/fuse/patch/ServerInstance.java) and a [patch pool](tool/src/main/java/com/redhat/fuse/patch/PatchPool.java) that contains the available patches.

The server and the pool can be queried like this respectively

```
> fusepatch --query-server
> fusepatch --query-pool 
```

The main function of the patch tool however is to update the server like this

```
> fusepatch --update-server
```

Updating the target server involves the following steps

1. query the server for the latest installed patch
2. contact the patch pool to compute a [smart patch](tool/src/main/java/com/redhat/fuse/patch/SmartPatch.java)
3. apply the smart patch, which supports file remove, replace and add operations
4. store smart patch metadata on the target server for the next query operation
