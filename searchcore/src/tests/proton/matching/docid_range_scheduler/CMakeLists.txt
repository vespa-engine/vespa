# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_docid_range_scheduler_test_app TEST
    SOURCES
    docid_range_scheduler_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_docid_range_scheduler_test_app COMMAND searchcore_docid_range_scheduler_test_app)
vespa_add_executable(searchcore_docid_range_scheduler_bench_app
    SOURCES
    docid_range_scheduler_bench.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_docid_range_scheduler_bench_app COMMAND searchcore_docid_range_scheduler_bench_app BENCHMARK)
