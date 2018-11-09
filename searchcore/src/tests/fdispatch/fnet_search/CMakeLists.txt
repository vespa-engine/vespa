# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_search_path_test_app TEST
    SOURCES
    search_path_test.cpp
    DEPENDS
    searchcore_fdispatch_search
)
vespa_add_test(NAME searchcore_search_path_test_app COMMAND searchcore_search_path_test_app)

vespa_add_executable(searchcore_search_coverage_test_app TEST
    SOURCES
    search_coverage_test.cpp
    DEPENDS
    searchcore_fdispatch_search
    searchcore_fdcommon
    searchcore_grouping
)
vespa_add_test(NAME searchcore_search_coverage_test_app COMMAND searchcore_search_coverage_test_app)
