# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_pingserver_app
    SOURCES
    packets.cpp
    pingserver.cpp
    DEPENDS
    fnet
)
vespa_add_executable(fnet_pingclient_app
    SOURCES
    packets.cpp
    pingclient.cpp
    DEPENDS
    fnet
)
