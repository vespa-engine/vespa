# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_external_data_value_test_app TEST
    SOURCES
    external_data_value_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_external_data_value_test_app COMMAND vespalib_external_data_value_test_app)
