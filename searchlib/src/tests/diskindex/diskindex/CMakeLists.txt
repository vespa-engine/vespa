# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_diskindex_test_app TEST
    SOURCES
    diskindex_test.cpp
    DEPENDS
    searchlib_test
)
vespa_add_test(NAME searchlib_diskindex_test_app COMMAND searchlib_diskindex_test_app)
