# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_registerapi_test_app TEST
    SOURCES
    registerapi.cpp
    DEPENDS
    vespa_slobrok_slobrokserver
    GTest::gtest
)
vespa_add_test(NAME slobrok_registerapi_test_app COMMAND slobrok_registerapi_test_app)
