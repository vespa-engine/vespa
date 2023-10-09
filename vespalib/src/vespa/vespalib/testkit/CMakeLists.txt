# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_testkit OBJECT
    SOURCES
    test_comparators.cpp
    test_hook.cpp
    test_master.cpp
    test_state_guard.cpp
    testapp.cpp
    time_bomb.cpp
    DEPENDS
)
