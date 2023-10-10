# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_frtconnectionpool_test_app TEST
    SOURCES
    frtconnectionpool.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_frtconnectionpool_test_app COMMAND config_frtconnectionpool_test_app)
