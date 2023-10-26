# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vdstestlib_dirconfig_test_app TEST
    SOURCES
    dirconfigtest.cpp
    DEPENDS
    vdstestlib
)
vespa_add_test(NAME vdstestlib_dirconfig_test_app NO_VALGRIND COMMAND vdstestlib_dirconfig_test_app)
