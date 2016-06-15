# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(persistence_testrunner_app
    SOURCES
    testrunner.cpp
    DEPENDS
    persistence_testdummyimpl
    persistence_testspi
    persistence
    persistence_persistence_conformancetest
)
vespa_add_test(NAME persistence_testrunner_app COMMAND persistence_testrunner_app)
