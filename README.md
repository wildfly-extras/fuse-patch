## Fuse Patch

A Fuse installer and patch utility.

The patch tool interacts with a target [server](core/src/main/java/org/wildfly/extras/patch/ServerInstance.java) and a [repository](core/src/main/java/org/wildfly/extras/patch/PatchRepository.java) that contains available packages.

The server and the repository can be queried like this respectively

```
> fusepatch --query-server
> fusepatch --query-repository
```

The main function of the patch tool however is to install a distribution from the repository like this

```
> fusepatch --install fuse-eap-distro-6.2.1-redhat
```

or update the server to the the latest like this

```
> fusepatch --update fuse-eap-distro
```

Please see the [User Guide](docs/UserGuide.md) for details.
