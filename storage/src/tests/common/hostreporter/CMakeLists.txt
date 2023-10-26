# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testhostreporter TEST
    SOURCES
    util.cpp
    DEPENDS
    storage
)

vespa_add_executable(storage_hostreporter_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    hostinfotest.cpp
    versionreportertest.cpp
    DEPENDS
    storage
    storage_testhostreporter
    GTest::GTest
)

vespa_add_test(
    NAME storage_hostreporter_gtest_runner_app
    COMMAND storage_hostreporter_gtest_runner_app
)
