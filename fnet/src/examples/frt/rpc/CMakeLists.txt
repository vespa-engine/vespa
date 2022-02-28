# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_rpc_server_app
    SOURCES
    rpc_server.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_rpc_client_app
    SOURCES
    rpc_client.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_echo_client_app
    SOURCES
    echo_client.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_rpc_info_app
    SOURCES
    rpc_info.cpp
    OUTPUT_NAME vespa-rpc-info
    INSTALL bin
    DEPENDS
    fnet
)
vespa_add_executable(fnet_rpc_callback_server_app
    SOURCES
    rpc_callback_server.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_rpc_callback_client_app
    SOURCES
    rpc_callback_client.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_rpc_invoke_app
    SOURCES
    rpc_invoke.cpp
    OUTPUT_NAME vespa-rpc-invoke-bin
    INSTALL bin
    DEPENDS
    fnet
)
