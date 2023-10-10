# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_hex_number_test_app TEST
    SOURCES
    hex_number_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_hex_number_test_app COMMAND vbench_hex_number_test_app)
