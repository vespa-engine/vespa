# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_connection_spread_test_app TEST
    SOURCES
    connection_spread_test.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_connection_spread_test_app COMMAND fnet_connection_spread_test_app)
