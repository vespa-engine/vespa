# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_generic_peek_test_app TEST
    SOURCES
    generic_peek_test.cpp
    DEPENDS
    vespaeval
    GTest::gtest
)
vespa_add_test(NAME eval_generic_peek_test_app NO_VALGRIND COMMAND eval_generic_peek_test_app)
