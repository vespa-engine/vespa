# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_true_test_app TEST
    SOURCES
    true.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_true_test_app COMMAND vespalib_true_test_app)
