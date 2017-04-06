# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net OBJECT
    SOURCES
    selector.cpp
    server_socket.cpp
    socket.cpp
    socket_address.cpp
    socket_handle.cpp
    socket_spec.cpp
    DEPENDS
)
