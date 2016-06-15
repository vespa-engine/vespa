# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_error_test_app
    SOURCES
    error.cpp
    DEPENDS
)
vespa_add_test(NAME messagebus_test_error_test_app NO_VALGRIND COMMAND sh error_test.sh)
vespa_add_executable(messagebus_test_cpp-server-error_app
    SOURCES
    cpp-server.cpp
    DEPENDS
)
vespa_add_executable(messagebus_test_cpp-client-error_app
    SOURCES
    cpp-client.cpp
    DEPENDS
)
