# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_attribute_searchable_adapter_test_app TEST
    SOURCES
    attribute_searchable_adapter_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_attribute_searchable_adapter_test_app COMMAND searchlib_attribute_searchable_adapter_test_app)
vespa_add_executable(searchlib_attribute_weighted_set_blueprint_test_app TEST
    SOURCES
    attribute_weighted_set_blueprint_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_attribute_weighted_set_blueprint_test_app COMMAND searchlib_attribute_weighted_set_blueprint_test_app)
vespa_add_executable(searchlib_attribute_blueprint_test_app TEST
    SOURCES
    attributeblueprint_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_attribute_blueprint_test_app COMMAND searchlib_attribute_blueprint_test_app)
