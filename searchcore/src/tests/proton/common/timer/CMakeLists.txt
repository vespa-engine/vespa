# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_common_timer_test_app TEST
    SOURCES
    timer_test.cpp
    DEPENDS
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_common_timer_test_app COMMAND searchcore_common_timer_test_app)
