# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_lid_state_vector_test_app TEST
    SOURCES
    lid_state_vector_test.cpp
    DEPENDS
    searchcore_documentmetastore
    GTest::GTest
)
vespa_add_test(NAME searchcore_lid_state_vector_test_app COMMAND searchcore_lid_state_vector_test_app)

