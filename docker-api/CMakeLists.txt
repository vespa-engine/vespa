# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(docker-api)
install(DIRECTORY DESTINATION conf/node-admin-app/components)
install_symlink(lib/jars/docker-api-jar-with-dependencies.jar conf/node-admin-app/components/docker-api.jar)
