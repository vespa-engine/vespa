# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(eval_array_array_map_test_app TEST
    SOURCES
    array_array_map_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_array_array_map_test_app COMMAND eval_array_array_map_test_app)
