# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_index_writer_test_app TEST
    SOURCES
    index_writer_test.cpp
    DEPENDS
    searchcore_test
    searchcore_index
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_index_writer_test_app COMMAND searchcore_index_writer_test_app)
