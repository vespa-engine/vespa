# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence
    SOURCES
    $<TARGET_OBJECTS:persistence_dummyimpl>
    $<TARGET_OBJECTS:persistence_spi>
    INSTALL lib64
    DEPENDS
)
vespa_add_library(persistence_persistence_conformancetest
    SOURCES
    $<TARGET_OBJECTS:persistence_conformancetest_lib>
    DEPENDS
    persistence
    GTest::GTest
)
