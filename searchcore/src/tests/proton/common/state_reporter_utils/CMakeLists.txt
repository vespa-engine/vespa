# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_state_reporter_utils_test_app TEST
    SOURCES
    state_reporter_utils_test.cpp
    DEPENDS
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_state_reporter_utils_test_app COMMAND searchcore_state_reporter_utils_test_app)
