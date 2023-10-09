# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_trace_test_app TEST
    SOURCES
    trace.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_executable(messagebus_test_cpp-server-trace_app
    SOURCES
    cpp-server.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_test(NAME messagebus_test_trace_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/trace_test.sh
               DEPENDS messagebus_test_trace_test_app messagebus_test_cpp-server-trace_app)
