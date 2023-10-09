# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logd_dummyserver_app
    SOURCES
    dummyserver.cpp
    DEPENDS
)
vespa_add_executable(logd_dummylogger_app
    SOURCES
    dummylogger.cpp
    DEPENDS
)
vespa_add_test(NAME logd_rotate_test NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/rotate_test.sh BENCHMARK
               DEPENDS logd_dummyserver_app logd_dummylogger_app)
