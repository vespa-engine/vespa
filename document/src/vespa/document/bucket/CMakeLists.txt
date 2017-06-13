# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_bucket OBJECT
    SOURCES
    bucket.cpp
    bucketdistribution.cpp
    bucketid.cpp
    bucketidfactory.cpp
    bucketidlist.cpp
    bucketselector.cpp
    bucketspace.cpp
    DEPENDS
    AFTER
    document_documentconfig
)
