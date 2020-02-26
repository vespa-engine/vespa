# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_sequencedtaskexecutor_benchmark_app TEST
    SOURCES
    sequencedtaskexecutor_benchmark.cpp
    DEPENDS
    searchlib
)

vespa_add_executable(searchlib_sequencedtaskexecutor_test_app TEST
    SOURCES
    sequencedtaskexecutor_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_sequencedtaskexecutor_test_app COMMAND searchlib_sequencedtaskexecutor_test_app)
