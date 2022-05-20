# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(node-repository-jar-with-dependencies.jar)

install(FILES src/main/config/node-repository.xml
        DESTINATION conf/configserver-app)
