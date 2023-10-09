# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_time_queue_test_app TEST
    SOURCES
    time_queue_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_time_queue_test_app COMMAND vbench_time_queue_test_app)
