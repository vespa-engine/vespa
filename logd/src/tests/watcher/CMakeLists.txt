# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(GTest REQUIRED)
vespa_add_executable(logd_watcher_test_app TEST
    SOURCES
    watcher_test.cpp
    DEPENDS
    logd
    GTest::GTest
)
vespa_add_test(NAME logd_watcher_test_app COMMAND logd_watcher_test_app)
