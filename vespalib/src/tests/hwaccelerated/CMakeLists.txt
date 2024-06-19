# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_hwaccelerated_test_app TEST
    SOURCES
    hwaccelerated_test.cpp
    DEPENDS
    vespalib
    vespa_hwaccelerated
)
vespa_add_test(NAME vespalib_hwaccelerated_test_app COMMAND vespalib_hwaccelerated_test_app)

vespa_add_executable(vespalib_hwaccelerated_bench_app
    SOURCES
    hwaccelerated_bench.cpp
    DEPENDS
    vespalib
    vespa_hwaccelerated
)
