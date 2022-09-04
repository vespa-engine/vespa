# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_match_phase_limiter_test_app TEST
    SOURCES
    match_phase_limiter_test.cpp
    DEPENDS
    searchcore_matching
    searchlib_test
)
vespa_add_test(NAME searchcore_match_phase_limiter_test_app COMMAND searchcore_match_phase_limiter_test_app)
