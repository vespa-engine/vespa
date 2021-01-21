# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(zookeeper-server-3.6.2)
# Needs to be included when this is the wanted default version (and symlinks for other versions need to be removed)
install_symlink(lib/jars/zookeeper-server-3.6.2-jar-with-dependencies.jar lib/jars/zookeeper-server-jar-with-dependencies.jar)
