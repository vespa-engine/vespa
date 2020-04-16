# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_reusable_set_test_app TEST
    SOURCES
    reusable_set_test.cpp
    DEPENDS
    vespalib
    gtest
)
vespa_add_test(NAME vespalib_reusable_set_test_app COMMAND vespalib_reusable_set_test_app)
