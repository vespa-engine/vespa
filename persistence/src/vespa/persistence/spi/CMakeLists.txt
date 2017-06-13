# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence_spi OBJECT
    SOURCES
    bucket.cpp
    bucketinfo.cpp
    exceptions.cpp
    persistenceprovider.cpp
    partitionstate.cpp
    abstractpersistenceprovider.cpp
    clusterstate.cpp
    context.cpp
    metricpersistenceprovider.cpp
    read_consistency.cpp
    result
    selection.cpp
    docentry
    DEPENDS
)
