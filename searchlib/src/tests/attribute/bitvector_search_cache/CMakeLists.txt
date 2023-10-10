# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_bitvector_search_cache_test_app TEST
    SOURCES
    bitvector_search_cache_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_bitvector_search_cache_test_app COMMAND searchlib_bitvector_search_cache_test_app)
