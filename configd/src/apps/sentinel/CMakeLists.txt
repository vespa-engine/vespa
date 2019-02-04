# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configd_config-sentinel_app
    SOURCES
    sentinel.cpp
    service.cpp
    config-handler.cpp
    line-splitter.cpp
    command-connection.cpp
    output-connection.cpp
    metrics.cpp
    state-api.cpp
    cmdq.cpp
    rpchooks.cpp
    rpcserver.cpp
    OUTPUT_NAME vespa-config-sentinel
    INSTALL sbin
    DEPENDS
    fnet
    configdefinitions
    staging_vespalib
)
