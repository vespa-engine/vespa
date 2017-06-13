# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vdstestlib_testrunner_app TEST
    SOURCES
    testrunner.cpp
    cppunittest.cpp
    DEPENDS
    vdstestlib
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME vdstestlib_testrunner_app
    NO_VALGRIND
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:vdstestlib_testrunner_app>
    DEPENDS vdstestlib_testrunner_app
)
