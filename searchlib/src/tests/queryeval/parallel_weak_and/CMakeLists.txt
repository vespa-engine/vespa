# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_parallel_weak_and_test_app TEST
    SOURCES
    parallel_weak_and_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_parallel_weak_and_test_app COMMAND searchlib_parallel_weak_and_test_app)
