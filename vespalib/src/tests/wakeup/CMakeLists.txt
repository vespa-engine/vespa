# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_wakeup_bench_app TEST
    SOURCES
    wakeup_bench.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
