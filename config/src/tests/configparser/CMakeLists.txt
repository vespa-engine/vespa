# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_configparser_test_app TEST
    SOURCES
    configparser.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_configparser_test_app COMMAND config_configparser_test_app)
vespa_generate_config(config_configparser_test_app ../../test/resources/configdefinitions/foo.def)
