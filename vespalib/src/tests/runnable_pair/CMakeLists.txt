# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_runnable_pair_test_app TEST
    SOURCES
    runnable_pair_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_runnable_pair_test_app COMMAND vespalib_runnable_pair_test_app)
