# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testpersistence TEST
    SOURCES
    processalltest.cpp
    persistencetestutils.cpp
    splitbitdetectortest.cpp
    legacyoperationhandlertest.cpp
    diskmoveoperationhandlertest.cpp
    providershutdownwrappertest.cpp
    mergehandlertest.cpp
    persistencethread_splittest.cpp
    bucketownershipnotifiertest.cpp
    persistencequeuetest.cpp
    testandsettest.cpp
    DEPENDS
    storage
    storage_testdistributor
    storage_testpersistence_common
)
