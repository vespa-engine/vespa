# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(container-disc)

vespa_install_script(src/main/sh/vespa-start-container-daemon.sh vespa-start-container-daemon bin)

install(FILES src/main/resources/configdefinitions/container.jdisc.config.http-server.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jdisc-bindings.def RENAME container.jdisc.jdisc-bindings.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jersey-connection.def RENAME container.config.jersey.jersey-connection.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jersey-init.def RENAME container.config.jersey.jersey-init.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/jersey-web-app-pool.def RENAME container.config.jersey.jersey-web-app-pool.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/metric-defaults.def RENAME container.jdisc.config.metric-defaults.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/score-board.def RENAME jdisc.metrics.yamasconsumer.cloud.score-board.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)


