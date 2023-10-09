# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(jrt_test_mockup-server_app TEST
    SOURCES
    mockup-server.cpp
    DEPENDS
)
vespa_add_test(NAME jrt_test_mockup-server_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/mockup-invoke_test.sh
               DEPENDS jrt_test_mockup-server_app)
