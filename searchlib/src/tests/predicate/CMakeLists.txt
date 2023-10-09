# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_predicate_index_test_app TEST
    SOURCES
    predicate_index_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_index_test_app COMMAND searchlib_predicate_index_test_app)
vespa_add_executable(searchlib_simple_index_test_app TEST
    SOURCES
    simple_index_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_simple_index_test_app COMMAND searchlib_simple_index_test_app)
vespa_add_executable(searchlib_tree_crumbs_test_app TEST
    SOURCES
    tree_crumbs_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_tree_crumbs_test_app COMMAND searchlib_tree_crumbs_test_app)
vespa_add_executable(searchlib_predicate_tree_analyzer_test_app TEST
    SOURCES
    predicate_tree_analyzer_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_tree_analyzer_test_app COMMAND searchlib_predicate_tree_analyzer_test_app)
vespa_add_executable(searchlib_predicate_tree_annotator_test_app TEST
    SOURCES
    predicate_tree_annotator_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_tree_annotator_test_app COMMAND searchlib_predicate_tree_annotator_test_app)
vespa_add_executable(searchlib_predicate_interval_store_test_app TEST
    SOURCES
    predicate_interval_store_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_interval_store_test_app COMMAND searchlib_predicate_interval_store_test_app)
vespa_add_executable(searchlib_document_features_store_test_app TEST
    SOURCES
    document_features_store_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_document_features_store_test_app COMMAND searchlib_document_features_store_test_app)
vespa_add_executable(searchlib_predicate_ref_cache_test_app TEST
    SOURCES
    predicate_ref_cache_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_ref_cache_test_app COMMAND searchlib_predicate_ref_cache_test_app)
vespa_add_executable(searchlib_predicate_interval_posting_list_test_app TEST
    SOURCES
    predicate_interval_posting_list_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_interval_posting_list_test_app COMMAND searchlib_predicate_interval_posting_list_test_app)
vespa_add_executable(searchlib_predicate_bounds_posting_list_test_app TEST
    SOURCES
    predicate_bounds_posting_list_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_bounds_posting_list_test_app COMMAND searchlib_predicate_bounds_posting_list_test_app)
vespa_add_executable(searchlib_predicate_zero_constraint_posting_list_test_app TEST
    SOURCES
    predicate_zero_constraint_posting_list_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_zero_constraint_posting_list_test_app COMMAND searchlib_predicate_zero_constraint_posting_list_test_app)
vespa_add_executable(searchlib_predicate_zstar_compressed_posting_list_test_app TEST
    SOURCES
    predicate_zstar_compressed_posting_list_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_zstar_compressed_posting_list_test_app COMMAND searchlib_predicate_zstar_compressed_posting_list_test_app)
vespa_add_executable(searchlib_predicate_range_term_expander_test_app TEST
    SOURCES
    predicate_range_term_expander_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_range_term_expander_test_app COMMAND searchlib_predicate_range_term_expander_test_app)
