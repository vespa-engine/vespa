# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence_testspi
    SOURCES
    clusterstatetest.cpp
    DEPENDS
    persistence_persistence_conformancetest
    persistence
)
