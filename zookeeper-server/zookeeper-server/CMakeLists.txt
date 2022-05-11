# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(zookeeper-server-3.7.0)
# Make symlink so that we have a default version, should be done only in zookeeper-server module
install_symlink(lib/jars/zookeeper-server-3.7.0-jar-with-dependencies.jar lib/jars/zookeeper-server-jar-with-dependencies.jar)
