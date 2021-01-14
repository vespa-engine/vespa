# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_btree_test_app TEST
    SOURCES
    btree_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_btree_test_app COMMAND vespalib_btree_test_app COST 30)
vespa_add_executable(vespalib_frozenbtree_test_app TEST
    SOURCES
    frozenbtree_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_frozenbtree_test_app COMMAND vespalib_frozenbtree_test_app COST 30)
vespa_add_executable(vespalib_btreeaggregation_test_app TEST
    SOURCES
    btreeaggregation_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_btreeaggregation_test_app COMMAND vespalib_btreeaggregation_test_app COST 30)
vespa_add_executable(vespalib_iteratespeed_app
    SOURCES
    iteratespeed.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_iteratespeed_app COMMAND vespalib_iteratespeed_app BENCHMARK)
