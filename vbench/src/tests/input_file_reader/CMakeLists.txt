# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_input_file_reader_test_app TEST
    SOURCES
    input_file_reader_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_input_file_reader_test_app COMMAND vbench_input_file_reader_test_app)
