# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_hash_test_app TEST
    SOURCES
    hash_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_hash_test_app COMMAND vespalib_hash_test_app)
vespa_add_executable(vespalib_string_test_app TEST
    SOURCES
    string_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_string_test_app COMMAND vespalib_string_test_app)
vespa_add_executable(vespalib_vector_map_test_app TEST
    SOURCES
    vector_map_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_vector_map_test_app COMMAND vespalib_vector_map_test_app)
vespa_add_executable(vespalib_asciistream_test_app TEST
    SOURCES
    asciistream_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_asciistream_test_app COMMAND vespalib_asciistream_test_app)
vespa_add_executable(vespalib_hashtable_test_app TEST
    SOURCES
    hashtable_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_hashtable_test_app COMMAND vespalib_hashtable_test_app)
vespa_add_executable(vespalib_uniq_by_sort_map_hash_app
    SOURCES
    uniq_by_sort_map_hash.cpp
    DEPENDS
    vespalib
)
vespa_add_executable(vespalib_lookup_benchmark_app
    SOURCES
    lookup_benchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_lookup_benchmark_app COMMAND vespalib_lookup_benchmark_app BENCHMARK)
vespa_add_executable(vespalib_replace_variable_test_app TEST
    SOURCES
    replace_variable_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_replace_variable_test_app COMMAND vespalib_replace_variable_test_app)
vespa_add_executable(vespalib_lrucache_test_app TEST
    SOURCES
    lrucache.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_lrucache_test_app COMMAND vespalib_lrucache_test_app)
vespa_add_executable(vespalib_cache_test_app TEST
    SOURCES
    cache_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_cache_test_app COMMAND vespalib_cache_test_app)
