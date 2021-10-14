# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_websocket_test_app TEST
    SOURCES
    websocket_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_websocket_test_app COMMAND vespalib_websocket_test_app)
vespa_add_executable(vespalib_websocket_server_app
    SOURCES
    websocket_server.cpp
    DEPENDS
    vespalib
)
