# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_grouping_test_app TEST
    SOURCES
    grouping.cpp
    DEPENDS
    searchcore_grouping
    searchcore_matching
)
vespa_add_test(NAME searchcore_grouping_test_app COMMAND searchcore_grouping_test_app)
