# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(DIRECTORY DESTINATION logs/vespa/node-admin)
install(FILES target/node-admin-jar-with-dependencies.jar DESTINATION conf/node-admin-app/components)
install_symlink(lib/jars/application-model-jar-with-dependencies.jar conf/node-admin-app/components/application-model-jar-with-dependencies.jar)
install_symlink(lib/jars/flags-jar-with-dependencies.jar conf/node-admin-app/components/flags-jar-with-dependencies.jar)
install(FILES src/main/application/services.xml DESTINATION conf/node-admin-app)
install(PROGRAMS src/main/sh/node-admin.sh DESTINATION libexec/vespa)
