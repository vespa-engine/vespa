# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_attributeguard_test_app TEST
    SOURCES
    attributeguard_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_attributeguard_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/attributeguard_test.sh
               DEPENDS searchlib_attributeguard_test_app)
