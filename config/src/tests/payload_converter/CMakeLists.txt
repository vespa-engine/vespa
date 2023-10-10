# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_payload_converter_test_app TEST
    SOURCES
    payload_converter.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_payload_converter_test_app COMMAND config_payload_converter_test_app)
