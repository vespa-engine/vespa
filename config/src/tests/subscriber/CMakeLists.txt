# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_subscriber_test_app TEST
    SOURCES
    subscriber_test.cpp
    DEPENDS
    vespa_config
    GTest::gtest
)
vespa_add_test(NAME config_subscriber_test_app COMMAND config_subscriber_test_app)
vespa_generate_config(config_subscriber_test_app ../../test/resources/configdefinitions/foo.def)
vespa_generate_config(config_subscriber_test_app ../../test/resources/configdefinitions/bar.def)
vespa_generate_config(config_subscriber_test_app ../../test/resources/configdefinitions/baz.def)
