# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_feed_and_search_test_app TEST
    SOURCES
    feed_and_search.cpp
    DEPENDS
    searchlib_test
)
vespa_add_test(NAME searchcore_feed_and_search_test_app COMMAND searchcore_feed_and_search_test_app)
