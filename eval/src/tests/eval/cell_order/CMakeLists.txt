# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_test_executable(eval_cell_order_test_app TEST
    SOURCES
    cell_order_test.cpp
    DEPENDS
    vespaeval
    GTest::gtest
)
