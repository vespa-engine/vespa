# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib_testdistribution
    SOURCES
    distributiontest.cpp
    global_bucket_space_distribution_test.cpp
    grouptest.cpp
    DEPENDS
    vespa_vdslib
    GTest::gtest
    GTest::gmock
)
