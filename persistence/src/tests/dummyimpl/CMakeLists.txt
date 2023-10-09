# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(persistence_dummyimpl_conformance_test_app TEST
    SOURCES
    dummyimpltest.cpp
    DEPENDS
    persistence_persistence_conformancetest
    persistence
)

vespa_add_test(
    NAME persistence_dummyimpl_conformance_test_app
    COMMAND persistence_dummyimpl_conformance_test_app
)

vespa_add_executable(persistence_dummypersistence_test_app TEST
    SOURCES
    dummypersistence_test.cpp
    DEPENDS
    persistence
)
vespa_add_test(NAME persistence_dummypersistence_test_app COMMAND persistence_dummypersistence_test_app)
