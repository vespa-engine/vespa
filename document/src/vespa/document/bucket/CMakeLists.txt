# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_bucket OBJECT
    SOURCES
    bucket.cpp
    bucketid.cpp
    bucketidfactory.cpp
    bucketidlist.cpp
    bucketselector.cpp
    bucketspace.cpp
    fixed_bucket_spaces.cpp
    DEPENDS
)
