# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_compact_buffer_candidates_test_app TEST
    SOURCES
    compact_buffer_candidates_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_compact_buffer_candidates_test_app COMMAND vespalib_compact_buffer_candidates_test_app)
