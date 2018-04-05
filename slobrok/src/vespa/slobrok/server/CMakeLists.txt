# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(slobrok_slobrokserver
    SOURCES
    cmd.cpp
    configshim.cpp
    exchange_manager.cpp
    history.cpp
    i_rpc_server_manager.cpp
    i_monitored_server.cpp
    managed_rpc_server.cpp
    monitor.cpp
    named_service.cpp
    remote_check.cpp
    remote_slobrok.cpp
    reserved_name.cpp
    rpc_server_manager.cpp
    rpc_server_map.cpp
    rpchooks.cpp
    rpcmirror.cpp
    sbenv.cpp
    selfcheck.cpp
    slobrokserver.cpp
    visible_map.cpp
    metrics_producer.cpp
    INSTALL lib64
    DEPENDS
    slobrok
)
