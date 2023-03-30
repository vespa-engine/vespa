# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_updater_test_app TEST
    SOURCES
    attribute_updater_test.cpp
    DEPENDS
    searchcore_pcommon
    searchlib_test
)
vespa_add_test(NAME searchcore_attribute_updater_test_app COMMAND searchcore_attribute_updater_test_app)
