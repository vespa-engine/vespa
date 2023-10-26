# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_hitcollector_test_app TEST
    SOURCES
    hitcollector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_hitcollector_test_app COMMAND searchlib_hitcollector_test_app)
vespa_add_executable(searchlib_sorted_hit_sequence_test_app TEST
    SOURCES
    sorted_hit_sequence_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_sorted_hit_sequence_test_app COMMAND searchlib_sorted_hit_sequence_test_app)
