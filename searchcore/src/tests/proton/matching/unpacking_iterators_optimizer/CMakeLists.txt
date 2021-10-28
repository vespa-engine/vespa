# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_unpacking_iterators_optimizer_test_app TEST
    SOURCES
    unpacking_iterators_optimizer_test.cpp
    DEPENDS
    searchcore_matching
    GTest::GTest
)
vespa_add_test(NAME searchcore_unpacking_iterators_optimizer_test_app COMMAND searchcore_unpacking_iterators_optimizer_test_app)
