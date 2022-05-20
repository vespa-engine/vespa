# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_shutdownguard_test_app TEST
    SOURCES
    shutdownguard_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_shutdownguard_test_app NO_VALGRIND COMMAND vespalib_shutdownguard_test_app)
