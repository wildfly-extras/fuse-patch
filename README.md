## Fuse Patch

A simple Fuse patch utility.

In a first step, you can generate some metadata from an existing zip

```
> fusepatch --build-ref some-distro.zip
```

Subsequent distro zip files can be reduced by filtering out entries that were identical in the referenced metadata obtained from a previous distro

```
> fusepatch --ref=some-distro.metadata some-distro-next.zip
```

### Example

```
> fusepatch --buildref wildfly-camel-patch-2.2.0.zip 
Patch metadata generated: .../wildfly-camel-patch-2.2.0.metadata

> fusepatch --ref=.../wildfly-camel-patch-2.2.0.metadata wildfly-camel-patch-2.3.0.zip
Patch generated: .../wildfly-camel-patch-2.3.0-fusepatch.zip

> ls -l .../wildfly-camel-*.zip
-rw-r--r--  1 user  staff  105926382 May 13 16:55 .../wildfly-camel-patch-2.2.0.zip
-rw-r--r--  1 user  staff   83021472 May 13 17:01 .../wildfly-camel-patch-2.3.0-fusepatch.zip
-rw-r--r--  1 user  staff  140056300 May 13 16:59 .../wildfly-camel-patch-2.3.0.zip
```

The patch has been reduces from 140MB to 83MB
