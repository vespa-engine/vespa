# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_rw_spin_lock_test_app TEST
    SOURCES
    rw_spin_lock_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_rw_spin_lock_test_app NO_VALGRIND COMMAND vespalib_rw_spin_lock_test_app)
