# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_field_index_test_app TEST
    SOURCES
    field_index_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_field_index_test_app COMMAND searchlib_field_index_test_app COST 100)

vespa_add_executable(searchlib_field_index_iterator_test_app TEST
    SOURCES
    field_index_iterator_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_field_index_iterator_test_app COMMAND searchlib_field_index_iterator_test_app)
