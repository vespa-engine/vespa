# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_task_runner_test_app TEST
    SOURCES
    task_runner_test.cpp
    DEPENDS
    searchcore_initializer
)
vespa_add_test(NAME searchcore_task_runner_test_app COMMAND searchcore_task_runner_test_app)
