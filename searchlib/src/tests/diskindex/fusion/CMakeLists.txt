# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_fusion_test_app TEST
    SOURCES
    fusion_test.cpp
    DEPENDS
    searchlib_test
    GTest::GTest
    AFTER
    searchlib_vespa-index-inspect_app
)
vespa_add_test(NAME searchlib_fusion_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/fusion_test.sh
               DEPENDS searchlib_fusion_test_app)
