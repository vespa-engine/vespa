# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_barrier_bench_app
    SOURCES
    barrier_bench.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
