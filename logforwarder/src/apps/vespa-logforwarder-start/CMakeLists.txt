# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logforwarder-start_app
    SOURCES
    main.cpp
    splunk-stopper.cpp
    splunk-starter.cpp
    cf-handler.cpp
    child-handler.cpp
    OUTPUT_NAME vespa-logforwarder-start
    INSTALL bin
    DEPENDS
    config_cloudconfig
    configdefinitions
    vespalib
)
