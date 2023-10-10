# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_matchengine_test_app TEST
    SOURCES
    matchengine_test.cpp
    DEPENDS
    searchcore_matchengine
    searchcore_matching
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_matchengine_test_app COMMAND searchcore_matchengine_test_app)
