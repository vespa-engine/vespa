# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_pow_as_map_optimizer_test_app TEST
    SOURCES
    pow_as_map_optimizer_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_pow_as_map_optimizer_test_app COMMAND eval_pow_as_map_optimizer_test_app)
