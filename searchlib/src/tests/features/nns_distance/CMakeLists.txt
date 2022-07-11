# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(searchlib_nns_distance_test_app TEST
    SOURCES
    nns_distance_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_nns_distance_test_app COMMAND searchlib_nns_distance_test_app)
