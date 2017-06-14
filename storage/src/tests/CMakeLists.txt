# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storage_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storage_teststorageserver
    storage_testbucketmover
    storage_teststorageutil
    storage_testvisiting
    storage_testbucketdb
    storage_testcommon
    storage_testhostreporter
    storage_testdistributor
    storage_testpersistence
    storage_testfilestorage
    storage_testmemory
    storage_teststatus
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME storage_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 8 $<TARGET_FILE:storage_testrunner_app>
    DEPENDS storage_testrunner_app
)
