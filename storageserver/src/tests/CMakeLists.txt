# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

vespa_add_test(
    NAME storageserver_testrunner_app
    COMMAND storageserver_testrunner_app
)
