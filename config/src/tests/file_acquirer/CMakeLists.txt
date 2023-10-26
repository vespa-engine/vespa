# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_file_acquirer_test_app TEST
    SOURCES
    file_acquirer_test.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_file_acquirer_test_app COMMAND config_file_acquirer_test_app)
