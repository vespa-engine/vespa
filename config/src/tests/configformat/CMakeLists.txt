# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_configformat_test_app TEST
    SOURCES
    configformat_test.cpp
    DEPENDS
    vespa_config
    GTest::gtest
)
vespa_add_test(NAME config_configformat_test_app COMMAND config_configformat_test_app)
vespa_generate_config(config_configformat_test_app ../../test/resources/configdefinitions/my.def)
