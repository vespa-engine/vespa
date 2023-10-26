# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_btree_store_test_app TEST
    SOURCES
    btree_store_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_btree_store_test_app COMMAND vespalib_btree_store_test_app COST 30)
