# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_applyattrupdates_test_app TEST
    SOURCES
    applyattrupdates.cpp
    DEPENDS
    searchcore_pcommon
    searchcore_util
)
vespa_add_test(NAME searchcore_applyattrupdates_test_app COMMAND searchcore_applyattrupdates_test_app)
