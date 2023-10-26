# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_backoff_test_app TEST
    SOURCES
    testbackoff.cpp
    DEPENDS
    slobrok_slobrokserver
)
vespa_add_test(NAME slobrok_backoff_test_app COMMAND slobrok_backoff_test_app)
