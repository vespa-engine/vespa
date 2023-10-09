# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configutil_config_status_test_app TEST
    SOURCES
    config_status_test.cpp
    DEPENDS
    configutil_util
)
vespa_add_test(NAME configutil_config_status_test_app COMMAND configutil_config_status_test_app)
