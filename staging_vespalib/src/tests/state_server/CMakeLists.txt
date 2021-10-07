# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_state_server_test_app TEST
    SOURCES
    state_server_test.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_state_server_test_app NO_VALGRIND NO_VALGRIND COMMAND staging_vespalib_state_server_test_app ENVIRONMENT "VESPA_HOME=.")
