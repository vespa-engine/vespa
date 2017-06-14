# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testpersistence_common TEST
    SOURCES
    persistenceproviderwrapper.cpp
    filestortestfixture.cpp
    DEPENDS
    persistence
    storage_testcommon
)
