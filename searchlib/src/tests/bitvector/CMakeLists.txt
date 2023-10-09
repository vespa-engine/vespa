# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_bitvectorbenchmark_test_app
    SOURCES
    bitvectorbenchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_bitvectorbenchmark_test_app COMMAND searchlib_bitvectorbenchmark_test_app BENCHMARK)
