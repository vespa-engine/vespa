# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_getconfig_test_app TEST
    SOURCES
    getconfig.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_getconfig_test_app COMMAND config_getconfig_test_app)
vespa_generate_config(config_getconfig_test_app ../../test/resources/configdefinitions/my.def)
