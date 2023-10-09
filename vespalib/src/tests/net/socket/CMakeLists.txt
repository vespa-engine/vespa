# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_socket_test_app TEST
    SOURCES
    socket_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_socket_test_app COMMAND vespalib_socket_test_app)
vespa_add_executable(vespalib_socket_server_app
    SOURCES
    socket_server.cpp
    DEPENDS
    vespalib
)
vespa_add_executable(vespalib_socket_client_app
    SOURCES
    socket_client.cpp
    DEPENDS
    vespalib
)
