# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_error_test_app TEST
    SOURCES
    error.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_executable(messagebus_test_cpp-server-error_app
    SOURCES
    cpp-server.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_executable(messagebus_test_cpp-client-error_app
    SOURCES
    cpp-client.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_test(NAME messagebus_test_error_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/error_test.sh
               DEPENDS messagebus_test_error_test_app messagebus_test_cpp-server-error_app messagebus_test_cpp-client-error_app)
