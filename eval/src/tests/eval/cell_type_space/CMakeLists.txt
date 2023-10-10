# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_cell_type_space_test_app TEST
    SOURCES
    cell_type_space_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_cell_type_space_test_app COMMAND eval_cell_type_space_test_app)
