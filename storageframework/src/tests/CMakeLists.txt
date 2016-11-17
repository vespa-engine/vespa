# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storageframework_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storageframework_testclock
    storageframework_teststatus
    storageframework_testmemory
    storageframework_testthread
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME storageframework_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:storageframework_testrunner_app>
    DEPENDS storageframework_testrunner_app
)
