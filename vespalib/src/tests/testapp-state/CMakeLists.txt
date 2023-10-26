# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_testapp-state_test_app TEST
    SOURCES
    testapp-state.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_testapp-state_test_app COMMAND vespalib_testapp-state_test_app)
vespa_add_executable(vespalib_state_test_app
    SOURCES
    statetest.cpp
    DEPENDS
    vespalib
)
