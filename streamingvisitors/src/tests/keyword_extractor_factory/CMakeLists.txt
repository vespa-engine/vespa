# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(streamingvisitors_keyword_extractor_factory_test_app TEST
    SOURCES
    keyword_extractor_factory_test.cpp
    DEPENDS
    streamingvisitors
    GTest::GTest
)
vespa_add_test(NAME streamingvisitors_keyword_extractor_factory_test_app COMMAND streamingvisitors_keyword_extractor_factory_test_app)
