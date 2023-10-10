# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storageserver_gtest_runner_app TEST
    SOURCES
    storageservertest.cpp
    testhelper.cpp
    gtest_runner.cpp
    DEPENDS
    storageserver_storageapp
    vdstestlib
    GTest::GTest
)

vespa_add_test(
    NAME storageserver_gtest_runner_app
    COMMAND storageserver_gtest_runner_app
)
