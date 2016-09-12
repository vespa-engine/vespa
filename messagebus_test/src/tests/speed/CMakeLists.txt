# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_speed_test_app
    SOURCES
    speed.cpp
    DEPENDS
)
vespa_add_test(NAME messagebus_test_speed_test_app COMMAND messagebus_test_speed_test_app BENCHMARK)
vespa_add_executable(messagebus_test_cpp-server-speed_app
    SOURCES
    cpp-server.cpp
    DEPENDS
)
vespa_add_executable(messagebus_test_cpp-client-speed_app
    SOURCES
    cpp-client.cpp
    DEPENDS
)
