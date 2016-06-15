# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storageapi_testrunner_app
    SOURCES
    testrunner.cpp
    DEPENDS
    storageapi_testbuckets
    storageapi_testmessageapi
    storageapi_testmbusprot
    storageapi
)
vespa_add_test(NAME storageapi_testrunner_app COMMAND storageapi_testrunner_app)
