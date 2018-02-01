# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence_spi OBJECT
    SOURCES
    abstractpersistenceprovider.cpp
    bucket.cpp
    bucketinfo.cpp
    clusterstate.cpp
    context.cpp
    docentry.cpp
    exceptions.cpp
    metricpersistenceprovider.cpp
    partitionstate.cpp
    persistenceprovider.cpp
    read_consistency.cpp
    result.cpp
    selection.cpp
    test.cpp
    DEPENDS
)
