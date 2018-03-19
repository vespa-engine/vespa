# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testpersistence TEST
    SOURCES
    bucketownershipnotifiertest.cpp
    diskmoveoperationhandlertest.cpp
    mergehandlertest.cpp
    persistencequeuetest.cpp
    persistencetestutils.cpp
    persistencethread_splittest.cpp
    processalltest.cpp
    provider_error_wrapper_test.cpp
    splitbitdetectortest.cpp
    testandsettest.cpp
    DEPENDS
    storage
    storage_testdistributor
    storage_testpersistence_common
)
