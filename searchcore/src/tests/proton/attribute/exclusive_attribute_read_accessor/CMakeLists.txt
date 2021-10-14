# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_exclusive_attribute_read_accessor_test_app TEST
    SOURCES
    exclusive_attribute_read_accessor_test.cpp
    DEPENDS
    searchcore_attribute
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_exclusive_attribute_read_accessor_test_app COMMAND searchcore_exclusive_attribute_read_accessor_test_app)
