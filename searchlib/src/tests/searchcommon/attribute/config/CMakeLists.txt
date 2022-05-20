# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcommon_attribute_config_test_app TEST
    SOURCES
    attribute_config_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchcommon_attribute_config_test_app NO_VALGRIND COMMAND searchcommon_attribute_config_test_app)
