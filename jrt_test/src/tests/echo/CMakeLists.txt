# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(jrt_test_echo-client_app TEST
    SOURCES
    echo-client.cpp
    DEPENDS
)
vespa_add_test(NAME jrt_test_echo-client_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/echo_test.sh
               DEPENDS jrt_test_echo-client_app)
