# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_misc_test_app TEST
    SOURCES
    misc_test.cpp
    DEPENDS
    vespa_config
    GTest::gtest
)
vespa_add_executable(config_configsystem_test_app TEST
    SOURCES
    configsystem_test.cpp
    DEPENDS
    vespa_config
    GTest::gtest
)
vespa_add_test(NAME config_misc_test_app COMMAND config_misc_test_app)
vespa_add_test(NAME config_configsystem_test_app COMMAND config_configsystem_test_app)
