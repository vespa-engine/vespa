# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vdslib_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    vdslib_containertest
    vdslib_testdistribution
    vdslib_teststate
    vdslib_testthread
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME vdslib_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:vdslib_testrunner_app>
    DEPENDS vdslib_testrunner_app
)
