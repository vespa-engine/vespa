# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_java_artifact(config-model-fat)

install(FILES src/main/resources/config-models.xml
        DESTINATION conf/configserver-app)
