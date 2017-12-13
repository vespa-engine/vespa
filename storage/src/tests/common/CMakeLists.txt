# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testcommon TEST
    SOURCES
    dummystoragelink.cpp
    global_bucket_space_distribution_converter_test.cpp
    metricstest.cpp
    storagelinktest.cpp
    testhelper.cpp
    testnodestateupdater.cpp
    teststorageapp.cpp
    DEPENDS
    storage
)
