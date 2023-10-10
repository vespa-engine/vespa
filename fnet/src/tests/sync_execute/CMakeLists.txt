# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_sync_execute_test_app TEST
    SOURCES
    sync_execute.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_sync_execute_test_app COMMAND fnet_sync_execute_test_app)
