# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configd_config-sentinel_app
    SOURCES
    cmdq.cpp
    config-owner.cpp
    env.cpp
    line-splitter.cpp
    manager.cpp
    metrics.cpp
    output-connection.cpp
    rpchooks.cpp
    rpcserver.cpp
    sentinel.cpp
    service.cpp
    state-api.cpp
    OUTPUT_NAME vespa-config-sentinel
    INSTALL sbin
    DEPENDS
    fnet
    configdefinitions
    staging_vespalib
)
