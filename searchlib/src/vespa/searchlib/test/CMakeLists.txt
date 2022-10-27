# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_test
    SOURCES
    attribute_builder.cpp
    document_weight_attribute_helper.cpp
    doc_builder.cpp
    imported_attribute_fixture.cpp
    initrange.cpp
    make_attribute_map_lookup_node.cpp
    mock_attribute_context.cpp
    mock_attribute_manager.cpp
    searchiteratorverifier.cpp
    schema_builder.cpp
    string_field_builder.cpp
    $<TARGET_OBJECTS:searchlib_test_fakedata>
    $<TARGET_OBJECTS:searchlib_searchlib_test_diskindex>
    $<TARGET_OBJECTS:searchlib_test_gtest_migration>
    DEPENDS
    searchlib
    searchlib_searchlib_test_features
    searchlib_searchlib_test_memoryindex
    GTest::GTest
)

vespa_add_library(searchlib_test_gtest_migration OBJECT
    SOURCES
    initrange.cpp
    searchiteratorverifier.cpp
)

target_compile_definitions(searchlib_test_gtest_migration PRIVATE ENABLE_GTEST_MIGRATION)
