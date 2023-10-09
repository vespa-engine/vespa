# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_handle_manager_test_app TEST
    SOURCES
    handle_manager_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_handle_manager_test_app COMMAND vespalib_handle_manager_test_app)
