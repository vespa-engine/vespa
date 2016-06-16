# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vdslib_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    vdslib_containertest
    vdslib_testdistribution
    vdslib_teststate
    vdslib_testthread
    vdslib
)
vespa_add_test(NAME vdslib_testrunner_app COMMAND vdslib_testrunner_app)
