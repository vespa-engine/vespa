# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_enum_attribute_compaction_test_app TEST
    SOURCES
    enum_attribute_compaction_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_enum_attribute_compaction_test_app COMMAND searchlib_enum_attribute_compaction_test_app COST 100)
