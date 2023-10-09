# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_internal_max_reduce_prod_join_feature_test_app TEST
    SOURCES
	internal_max_reduce_prod_join_feature_test.cpp
    DEPENDS
        searchlib
)
vespa_add_test(NAME searchlib_internal_max_reduce_prod_join_feature_test_app COMMAND searchlib_internal_max_reduce_prod_join_feature_test_app)
