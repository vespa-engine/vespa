# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storageserver_testrunner_app TEST
    SOURCES
    storageservertest.cpp
    testhelper.cpp
    dummystoragelink.cpp
    testrunner.cpp
    DEPENDS
    storageserver_storageapp
    vdstestlib
    searchlib_searchlib_uca
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME storageserver_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:storageserver_testrunner_app>
    DEPENDS storageserver_testrunner_app
)
