# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_schedule_test_app TEST
    SOURCES
    schedule.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_schedule_test_app COMMAND fnet_schedule_test_app)
vespa_add_executable(fnet_sloweventloop_test_app TEST
    SOURCES
    sloweventloop.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_sloweventloop_test_app COMMAND fnet_sloweventloop_test_app)
