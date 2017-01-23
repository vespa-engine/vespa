# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_gbdt_test_app TEST
    SOURCES
    gbdt_test.cpp
    DEPENDS
    vespalib
    vespalib_vespalib_eval_llvm
)
vespa_add_test(NAME vespalib_gbdt_test_app COMMAND vespalib_gbdt_test_app)
vespa_add_executable(vespalib_gbdt_benchmark_app
    SOURCES
    gbdt_benchmark.cpp
    DEPENDS
    vespalib
    vespalib_vespalib_eval_llvm
)
vespa_add_test(NAME vespalib_gbdt_benchmark_app COMMAND vespalib_gbdt_benchmark_app BENCHMARK)
