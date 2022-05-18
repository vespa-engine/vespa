# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_sequencedtaskexecutor_benchmark_app TEST
    SOURCES
    sequencedtaskexecutor_benchmark.cpp
    DEPENDS
    vespalib
)

vespa_add_executable(vespalib_sequencedtaskexecutor_test_app TEST
    SOURCES
    sequencedtaskexecutor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_sequencedtaskexecutor_test_app COMMAND vespalib_sequencedtaskexecutor_test_app)

vespa_add_executable(vespalib_adaptive_sequenced_executor_test_app TEST
    SOURCES
    adaptive_sequenced_executor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_adaptive_sequenced_executor_test_app COMMAND vespalib_adaptive_sequenced_executor_test_app)

vespa_add_executable(vespalib_foregroundtaskexecutor_test_app TEST
    SOURCES
    foregroundtaskexecutor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_foregroundtaskexecutor_test_app COMMAND vespalib_foregroundtaskexecutor_test_app)
