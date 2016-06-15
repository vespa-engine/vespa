# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vdstestlib_testrunner_app
    SOURCES
    testrunner.cpp
    cppunittest.cpp
    DEPENDS
    vdstestlib
)
vespa_add_test(NAME vdstestlib_testrunner_app NO_VALGRIND COMMAND vdstestlib_testrunner_app)
