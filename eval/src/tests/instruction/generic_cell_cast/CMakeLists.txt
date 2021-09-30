# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_generic_cell_cast_test_app TEST
    SOURCES
    generic_cell_cast_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_generic_cell_cast_test_app COMMAND eval_generic_cell_cast_test_app)
