# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_persistence_handler_map_test_app TEST
    SOURCES
    persistence_handler_map_test.cpp
    DEPENDS
    searchcore_persistenceengine
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_persistence_handler_map_test_app COMMAND searchcore_persistence_handler_map_test_app)
