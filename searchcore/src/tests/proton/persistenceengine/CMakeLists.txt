# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_persistenceengine_test_app TEST
    SOURCES
    persistenceengine_test.cpp
    DEPENDS
    searchcore_persistenceengine
    searchcore_attribute
    searchcore_pcommon
    searchcore_proton_metrics
    searchcore_test
)
vespa_add_test(NAME searchcore_persistenceengine_test_app COMMAND searchcore_persistenceengine_test_app)
