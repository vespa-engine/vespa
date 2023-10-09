# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_slime_test_app TEST
    SOURCES
    slime_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_slime_test_app COMMAND vespalib_slime_test_app)
vespa_add_executable(vespalib_slime_inject_test_app TEST
    SOURCES
    slime_inject_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_slime_inject_test_app COMMAND vespalib_slime_inject_test_app)
vespa_add_executable(vespalib_slime_binary_format_test_app TEST
    SOURCES
    slime_binary_format_test.cpp
    type_traits.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_slime_binary_format_test_app COMMAND vespalib_slime_binary_format_test_app)
vespa_add_executable(vespalib_slime_json_format_test_app TEST
    SOURCES
    slime_json_format_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_slime_json_format_test_app COMMAND vespalib_slime_json_format_test_app)
vespa_add_executable(vespalib_json_slime_benchmark_app
    SOURCES
    json_slime_benchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_json_slime_benchmark_app COMMAND vespalib_json_slime_benchmark_app BENCHMARK)
