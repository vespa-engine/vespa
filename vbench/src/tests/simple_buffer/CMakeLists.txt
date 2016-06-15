# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_simple_buffer_test_app
    SOURCES
    simple_buffer_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_simple_buffer_test_app COMMAND vbench_simple_buffer_test_app)
