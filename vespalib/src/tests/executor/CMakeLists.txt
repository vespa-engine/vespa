# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_threadstackexecutor_test_app
    SOURCES
    threadstackexecutor_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_threadstackexecutor_test_app COMMAND vespalib_threadstackexecutor_test_app)
vespa_add_executable(vespalib_executor_test_app
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
