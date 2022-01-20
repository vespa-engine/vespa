# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_shared_operation_throttler_test_app TEST
    SOURCES
    shared_operation_throttler_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_shared_operation_throttler_test_app COMMAND vespalib_shared_operation_throttler_test_app)
