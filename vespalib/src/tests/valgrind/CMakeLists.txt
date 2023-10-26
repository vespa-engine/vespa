# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_valgrind_test_app TEST
    SOURCES
    valgrind_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_valgrind_test_app_testInitializedUser COMMAND vespalib_valgrind_test_app testInitializedUser NO_VALGRIND)
vespa_add_test(NAME vespalib_valgrind_test_app_testInitializedSystemCall COMMAND vespalib_valgrind_test_app testInitializedSystemCall NO_VALGRIND)
vespa_add_test(NAME vespalib_valgrind_test_app_testUninitializedUser COMMAND vespalib_valgrind_test_app testUninitializedUser NO_VALGRIND)
vespa_add_test(NAME vespalib_valgrind_test_app_testUninitializedSystemCall COMMAND vespalib_valgrind_test_app testUninitializedSystemCall NO_VALGRIND)
