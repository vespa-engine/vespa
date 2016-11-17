# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storageapi_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storageapi_testbuckets
    storageapi_testmessageapi
    storageapi_testmbusprot
    storageapi
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME storageapi_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:storageapi_testrunner_app>
    DEPENDS storageapi_testrunner_app
)
