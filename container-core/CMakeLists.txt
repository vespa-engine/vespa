# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/application-metadata.def RENAME container.core.application-metadata.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/container-document.def RENAME container.core.document.container-document.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/container-http.def RENAME container.core.container-http.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/diagnostics.def RENAME container.core.diagnostics.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/health-monitor.def RENAME container.jdisc.config.health-monitor.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/http-filter.def RENAME container.core.http.http-filter.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/metrics-presentation.def RENAME metrics.metrics-presentation.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/mockservice.def RENAME container.handler.test.mockservice.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-logging.def RENAME container.core.qr-logging.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-searchers.def RENAME container.qr-searchers.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr-templates.def RENAME container.core.qr-templates.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/qr.def RENAME container.qr.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/servlet-config.def RENAME container.servlet.servlet-config.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/threadpool.def RENAME container.handler.threadpool.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/vip-status.def RENAME container.core.vip-status.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
