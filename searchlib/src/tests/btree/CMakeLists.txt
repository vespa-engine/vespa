# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_btreeaggregation_test_app TEST
    SOURCES
    btreeaggregation_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_btreeaggregation_test_app COMMAND searchlib_btreeaggregation_test_app)
vespa_add_executable(searchlib_iteratespeed_app
    SOURCES
    iteratespeed.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_iteratespeed_app COMMAND searchlib_iteratespeed_app BENCHMARK)
