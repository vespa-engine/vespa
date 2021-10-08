# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_flushengine_prepare_restart_flush_strategy_test_app TEST
    SOURCES
    prepare_restart_flush_strategy_test.cpp
    DEPENDS
    searchcorespi
    searchcore_flushengine
)
vespa_add_test(
    NAME searchcore_flushengine_prepare_restart_flush_strategy_test_app
    COMMAND searchcore_flushengine_prepare_restart_flush_strategy_test_app
)
