# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_mirrorapi_test_app TEST
    SOURCES
    mirrorapi.cpp
    DEPENDS
    slobrok_slobrokserver
)
vespa_add_test(NAME slobrok_mirrorapi_test_app COMMAND slobrok_mirrorapi_test_app)
vespa_add_executable(slobrok_mirror_match_test_app TEST
    SOURCES
    match_test.cpp
    DEPENDS
    slobrok
)
vespa_add_test(NAME slobrok_mirror_match_test_app COMMAND slobrok_mirror_match_test_app)
