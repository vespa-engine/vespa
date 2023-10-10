# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_threadstackexecutor_test_app TEST
    SOURCES
    threadstackexecutor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_threadstackexecutor_test_app COMMAND vespalib_threadstackexecutor_test_app)
vespa_add_executable(vespalib_executor_test_app TEST
    SOURCES
    executor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_executor_test_app COMMAND vespalib_executor_test_app)
vespa_add_executable(vespalib_stress_test_app
    SOURCES
    stress_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_stress_test_app COMMAND vespalib_stress_test_app BENCHMARK)
vespa_add_executable(vespalib_blockingthreadstackexecutor_test_app TEST
    SOURCES
    blockingthreadstackexecutor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_blockingthreadstackexecutor_test_app COMMAND vespalib_blockingthreadstackexecutor_test_app)
vespa_add_executable(vespalib_blocking_executor_stress_test_app TEST
    SOURCES
    blocking_executor_stress.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_blocking_executor_stress_test_app COMMAND vespalib_blocking_executor_stress_test_app)
