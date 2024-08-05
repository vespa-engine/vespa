# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_predicate_vespa_test_app TEST
    SOURCES
    vespa_testrunner.cpp
    predicate_index_test.cpp
    simple_index_test.cpp
    tree_crumbs_test.cpp
    predicate_tree_analyzer_test.cpp
    predicate_tree_annotator_test.cpp
    predicate_interval_store_test.cpp
    document_features_store_test.cpp
    predicate_ref_cache_test.cpp
    predicate_interval_posting_list_test.cpp
    predicate_bounds_posting_list_test.cpp
    predicate_zero_constraint_posting_list_test.cpp
    predicate_zstar_compressed_posting_list_test.cpp
    predicate_range_term_expander_test.cpp
    DEPENDS
    vespa_searchlib
)
vespa_add_test(NAME searchlib_predicate_vespa_test_app COMMAND searchlib_predicate_vespa_test_app)
