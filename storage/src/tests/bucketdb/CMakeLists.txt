# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testbucketdb TEST
    SOURCES
    initializertest.cpp
    bucketmanagertest.cpp
    judyarraytest.cpp
    judymultimaptest.cpp
    lockablemaptest.cpp
    bucketinfotest.cpp
    distribution_hash_normalizer_test.cpp
    DEPENDS
    storage
    storage_testcommon
)
