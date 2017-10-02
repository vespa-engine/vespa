# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespa-logforwarder-start_app
    SOURCES
    main.cpp
    cf-handler.cpp
    sig-catch.cpp
    OUTPUT_NAME vespa-logforwarder-start_app
    INSTALL bin
    DEPENDS
    config_cloudconfig
)
vespa_generate_config(vespa-logforwarder-start_app logforwarder.def)
install(FILES logforwarder.def RENAME cloud.config.logforwarder.def DESTINATION var/db/vespa/config_server/serverdb/classes)
