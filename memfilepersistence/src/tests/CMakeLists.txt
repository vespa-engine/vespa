# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(memfilepersistence_testrunner_app
    SOURCES
    testhelper.cpp
    testrunner.cpp
    DEPENDS
    memfilepersistence_testconformance
    memfilepersistence_testdevices
    memfilepersistence_testinit
    memfilepersistence_testspi
    memfilepersistence_testtools
    memfilepersistence
)
vespa_add_test(NAME memfilepersistence_testrunner_app COMMAND memfilepersistence_testrunner_app)
