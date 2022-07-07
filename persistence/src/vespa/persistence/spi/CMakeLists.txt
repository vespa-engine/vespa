# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(persistence_spi OBJECT
    SOURCES
    abstractpersistenceprovider.cpp
    attribute_resource_usage.cpp
    bucket.cpp
    bucketinfo.cpp
    catchresult.cpp
    clusterstate.cpp
    context.cpp
    docentry.cpp
    exceptions.cpp
    id_and_timestamp.cpp
    persistenceprovider.cpp
    read_consistency.cpp
    resource_usage.cpp
    resource_usage_listener.cpp
    result.cpp
    selection.cpp
    test.cpp
    types.cpp
    DEPENDS
)
