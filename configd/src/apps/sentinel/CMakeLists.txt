# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configd_config-sentinel_app
    SOURCES
    check-completion-handler.cpp
    cmdq.cpp
    config-owner.cpp
    connectivity.cpp
    env.cpp
    line-splitter.cpp
    manager.cpp
    metrics.cpp
    model-owner.cpp
    output-connection.cpp
    outward-check.cpp
    peer-check.cpp
    report-connectivity.cpp
    rpchooks.cpp
    rpcserver.cpp
    sentinel.cpp
    service.cpp
    state-api.cpp
    status-callback.cpp
    OUTPUT_NAME vespa-config-sentinel
    INSTALL sbin
    DEPENDS
    fnet
    configdefinitions
    vespalib
)
