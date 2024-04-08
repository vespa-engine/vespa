# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(otelcol_start_app
    SOURCES
    main.cpp
    cf-handler.cpp
    child-handler.cpp
    file-watcher.cpp
    wrapper.cpp
    OUTPUT_NAME vespa-otelcol-start
    INSTALL bin
    DEPENDS
    config_cloudconfig
    configdefinitions
    vespalib
)
