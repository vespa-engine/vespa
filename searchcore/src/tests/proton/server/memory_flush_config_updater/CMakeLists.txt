# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_memory_flush_config_updater_test_app TEST
    SOURCES
    memory_flush_config_updater_test.cpp
    DEPENDS
    searchcore_server
    searchcore_flushengine
)
vespa_add_test(NAME searchcore_memory_flush_config_updater_test_app COMMAND searchcore_memory_flush_config_updater_test_app)
