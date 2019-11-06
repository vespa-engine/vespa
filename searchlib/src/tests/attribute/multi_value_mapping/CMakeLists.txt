# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_multi_value_mapping_test_app TEST
    SOURCES
    multi_value_mapping_test.cpp
    DEPENDS
    searchlib
    gtest
)
vespa_add_test(NAME searchlib_multi_value_mapping_test_app COMMAND searchlib_multi_value_mapping_test_app)
