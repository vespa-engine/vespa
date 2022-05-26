# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_test_app TEST
    SOURCES
    attribute_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_flushengine
    searchcore_pcommon
    searchcore_test
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchcore_attribute_test_app COMMAND searchcore_attribute_test_app)

vespa_add_executable(searchcore_attributeflush_test_app TEST
    SOURCES
    attributeflush_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_flushengine
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_attributeflush_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/attributeflush_test.sh
               DEPENDS searchcore_attributeflush_test_app)
