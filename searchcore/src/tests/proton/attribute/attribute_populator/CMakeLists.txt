# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_populator_test_app TEST
    SOURCES
    attribute_populator_test.cpp
    DEPENDS
    searchcore_attribute
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_attribute_populator_test_app COMMAND searchcore_attribute_populator_test_app)
