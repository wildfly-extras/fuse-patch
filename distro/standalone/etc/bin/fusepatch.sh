#!/bin/sh

PRG="$0"

# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

HOMEDIR=`dirname $PRG`/..

# Get absolute path of the HOMEDIR
CURDIR=`pwd`
HOMEDIR=`cd $HOMEDIR; pwd`
cd $CURDIR

java -jar \
        -Dlog4j.configuration=file://$HOMEDIR/config/logging.properties \
        -Dfusepatch.repository=file://$HOMEDIR/repository \
        $HOMEDIR/lib/fuse-patch-core-@project.version@.jar "$@"
