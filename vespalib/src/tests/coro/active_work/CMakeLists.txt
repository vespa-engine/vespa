# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_active_work_test_app TEST
    SOURCES
    active_work_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_active_work_test_app COMMAND vespalib_active_work_test_app)
