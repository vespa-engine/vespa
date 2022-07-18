# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_bucketdb STATIC
    SOURCES
    bucket_create_notifier.cpp
    bucket_db_explorer.cpp
    bucket_db_owner.cpp
    bucketdb.cpp
    bucketdbhandler.cpp
    bucketsessionbase.cpp
    bucketstate.cpp
    checksumaggregators.cpp
    joinbucketssession.cpp
    splitbucketsession.cpp
    DEPENDS
)
