# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_speed_test_app
    SOURCES
    speed.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_executable(messagebus_test_cpp-server-speed_app
    SOURCES
    cpp-server.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_executable(messagebus_test_cpp-client-speed_app
    SOURCES
    cpp-client.cpp
    DEPENDS
    messagebus_messagebus-test
)
vespa_add_test(NAME messagebus_test_speed_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/speed_test.sh BENCHMARK
               DEPENDS messagebus_test_speed_test_app messagebus_test_cpp-server-speed_app messagebus_test_cpp-client-speed_app )
