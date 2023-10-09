# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_configuri_test_app TEST
    SOURCES
    configuri_test.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_configuri_test_app COMMAND config_configuri_test_app)
vespa_generate_config(config_configuri_test_app ../../test/resources/configdefinitions/my.def)
