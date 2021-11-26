# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_hwaccelrated_test_app TEST
    SOURCES
    hwaccelrated_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_hwaccelrated_test_app COMMAND vespalib_hwaccelrated_test_app)

vespa_add_executable(vespalib_hwaccelrated_bench_app
    SOURCES
    hwaccelrated_bench.cpp
    DEPENDS
    vespalib
)
