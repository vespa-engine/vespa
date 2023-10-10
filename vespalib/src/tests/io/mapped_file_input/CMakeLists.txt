# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_mapped_file_input_test_app TEST
    SOURCES
    mapped_file_input_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_mapped_file_input_test_app COMMAND vespalib_mapped_file_input_test_app)
