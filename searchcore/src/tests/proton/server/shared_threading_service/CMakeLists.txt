# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_shared_threading_service_test_app TEST
    SOURCES
    shared_threading_service_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    GTest::GTest
)
vespa_add_test(NAME searchcore_shared_threading_service_test_app COMMAND searchcore_shared_threading_service_test_app)
