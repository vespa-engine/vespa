# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_timespeed_test_app TEST
    SOURCES
    timespeed.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_timespeed_test_app NO_VALGRIND COMMAND fnet_timespeed_test_app)
