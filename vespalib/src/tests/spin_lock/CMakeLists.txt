# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_spin_lock_test_app TEST
    SOURCES
    spin_lock_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_spin_lock_test_app COMMAND vespalib_spin_lock_test_app)
