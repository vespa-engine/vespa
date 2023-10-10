# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(jrt_test_extract-reflection_app TEST
    SOURCES
    extract-reflection.cpp
    DEPENDS
)
vespa_add_test(NAME jrt_test_extract-reflection_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/mandatory-methods_test.sh
               DEPENDS jrt_test_extract-reflection_app)
