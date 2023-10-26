# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_phrasesplitter_test_app TEST
    SOURCES
    phrasesplitter_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_phrasesplitter_test_app COMMAND searchlib_phrasesplitter_test_app)
vespa_add_executable(searchlib_benchmark_app
    SOURCES
    benchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_benchmark_app COMMAND searchlib_benchmark_app BENCHMARK)
