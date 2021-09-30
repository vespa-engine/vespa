# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
vespa_add_executable(metrics_gtest_runner_app TEST
    SOURCES
    countmetrictest.cpp
    metric_timer_test.cpp
    metricmanagertest.cpp
    metricsettest.cpp
    metrictest.cpp
    snapshottest.cpp
    stresstest.cpp
    summetrictest.cpp
    valuemetrictest.cpp
    gtest_runner.cpp
    DEPENDS
    metrics
    vdstestlib
    GTest::GTest
)

vespa_add_test(
    NAME metrics_gtest_runner_app
    COMMAND metrics_gtest_runner_app
    COST 200
)

