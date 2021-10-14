# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_websocket OBJECT
    SOURCES
    acceptor.cpp
    buffer.cpp
    connection.cpp
    frame.cpp
    handler.cpp
    key.cpp
    request.cpp
    websocket_server.cpp
    DEPENDS
)
