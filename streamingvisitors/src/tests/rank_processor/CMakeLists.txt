# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(streamingvisitors_rank_processor_test_app TEST
    SOURCES
    rank_processor_test.cpp
    DEPENDS
    streamingvisitors
    GTest::GTest
)
vespa_add_test(NAME streamingvisitors_rank_processor_test_app COMMAND streamingvisitors_rank_processor_test_app)
