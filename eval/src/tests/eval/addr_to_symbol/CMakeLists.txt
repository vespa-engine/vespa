# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_addr_to_symbol_test_app TEST
    SOURCES
    addr_to_symbol_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_addr_to_symbol_test_app COMMAND eval_addr_to_symbol_test_app)
