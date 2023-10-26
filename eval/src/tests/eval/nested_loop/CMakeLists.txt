# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_nested_loop_test_app TEST
    SOURCES
    nested_loop_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_nested_loop_test_app COMMAND eval_nested_loop_test_app)
vespa_add_executable(eval_nested_loop_bench_app TEST
    SOURCES
    nested_loop_bench.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
