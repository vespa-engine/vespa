# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_flushengine_flush_history_test_app TEST
    SOURCES
    flush_history_test.cpp
    DEPENDS
    searchcorespi
    searchcore_flushengine
    GTest::gtest
)
vespa_add_test(
    NAME searchcore_flushengine_flush_history_test_app
    COMMAND searchcore_flushengine_flush_history_test_app
)
