# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(memfilepersistence_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    memfilepersistence_testconformance
    memfilepersistence_testdevices
    memfilepersistence_testinit
    memfilepersistence_testspi
    memfilepersistence_testtools
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME memfilepersistence_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:memfilepersistence_testrunner_app>
    DEPENDS memfilepersistence_testrunner_app
)
