# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configutil_vespa-config-status_app
    SOURCES
    main.cpp
    OUTPUT_NAME vespa-config-status-bin
    INSTALL bin
    DEPENDS
    configutil_util
)
