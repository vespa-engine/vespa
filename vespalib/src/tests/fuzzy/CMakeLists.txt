# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_fuzzy_matcher_test_app TEST
        SOURCES
        fuzzy_matcher_test.cpp
        DEPENDS
        vespalib
        GTest::GTest
        )
vespa_add_test(NAME vespalib_fuzzy_matcher_test_app COMMAND vespalib_fuzzy_matcher_test_app)

vespa_add_executable(vespalib_levenshtein_distance_test_app TEST
        SOURCES
        levenshtein_distance_test.cpp
        DEPENDS
        vespalib
        GTest::GTest
        )
vespa_add_test(NAME vespalib_levenshtein_distance_test_app COMMAND vespalib_levenshtein_distance_test_app)
