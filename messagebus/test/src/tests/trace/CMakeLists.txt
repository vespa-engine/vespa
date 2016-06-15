# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_trace_test_app
    SOURCES
    trace.cpp
    DEPENDS
)
vespa_add_test(NAME messagebus_test_trace_test_app NO_VALGRIND COMMAND sh trace_test.sh)
vespa_add_executable(messagebus_test_cpp-server-trace_app
    SOURCES
    cpp-server.cpp
    DEPENDS
)
