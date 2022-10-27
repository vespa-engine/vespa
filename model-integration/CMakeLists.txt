# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(model-integration-jar-with-dependencies.jar)

install(FILES src/main/config/model-integration.xml DESTINATION conf/configserver-app)

install_config_definitions()
