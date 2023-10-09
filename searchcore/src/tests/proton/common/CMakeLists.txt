# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_selectpruner_test_app TEST
    SOURCES
    selectpruner_test.cpp
    DEPENDS
    searchcore_pcommon
    searchlib_test
)
vespa_add_test(NAME searchcore_selectpruner_test_app COMMAND searchcore_selectpruner_test_app)
vespa_add_executable(searchcore_cachedselect_test_app TEST
    SOURCES
    cachedselect_test.cpp
    DEPENDS
    searchcore_pcommon
    searchlib_test
)
vespa_add_test(NAME searchcore_cachedselect_test_app COMMAND searchcore_cachedselect_test_app)
vespa_add_executable(pendinglidtracker_test_app TEST
    SOURCES
    pendinglidtracker_test.cpp
    DEPENDS
    searchcore_pcommon
)
vespa_add_test(NAME pendinglidtracker_test_app COMMAND pendinglidtracker_test_app)
