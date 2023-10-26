# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_portal OBJECT
    SOURCES
    handle_manager.cpp
    http_connection.cpp
    http_request.cpp
    listener.cpp
    portal.cpp
    reactor.cpp
    DEPENDS
)
