# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence_testdummyimpl
    SOURCES
    dummyimpltest.cpp
    DEPENDS
    persistence_persistence_conformancetest
    persistence
)
vespa_add_executable(persistence_dummypersistence_test_app TEST
    SOURCES
    dummypersistence_test.cpp
    DEPENDS
    persistence
)
vespa_add_test(NAME persistence_dummypersistence_test_app COMMAND persistence_dummypersistence_test_app)
