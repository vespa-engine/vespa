# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_slime_are_equal_test_app TEST
    SOURCES
    slime_are_equal_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_slime_are_equal_test_app COMMAND vespalib_slime_are_equal_test_app)
