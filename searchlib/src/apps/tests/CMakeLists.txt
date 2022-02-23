# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_biglog_test_app
    SOURCES
    biglogtest.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_biglog_test_app COMMAND searchlib_biglog_test_app BENCHMARK)
vespa_add_executable(searchlib_memoryindexstress_test_app
    SOURCES
    memoryindexstress_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_memoryindexstress_test_app COMMAND searchlib_memoryindexstress_test_app BENCHMARK)
vespa_add_executable(searchlib_document_weight_attribute_lookup_stress_test_app
    SOURCES
    document_weight_attribute_lookup_stress_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_document_weight_attribute_lookup_stress_test_app COMMAND searchlib_document_weight_attribute_lookup_stress_test_app BENCHMARK)
