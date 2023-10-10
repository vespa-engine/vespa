# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_feedoperation_test_app TEST
    SOURCES
    feedoperation_test.cpp
    DEPENDS
    searchcore_feedoperation
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_feedoperation_test_app COMMAND searchcore_feedoperation_test_app)
