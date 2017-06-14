# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(metrics_testrunner_app TEST
    SOURCES
    testrunner.cpp
    countmetrictest.cpp
    valuemetrictest.cpp
    metricsettest.cpp
    summetrictest.cpp
    metricmanagertest.cpp
    loadmetrictest.cpp
    snapshottest.cpp
    stresstest.cpp
    metrictest.cpp
    metric_timer_test.cpp
    DEPENDS
    metrics
    vdstestlib
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME metrics_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 1 $<TARGET_FILE:metrics_testrunner_app>
    DEPENDS metrics_testrunner_app
)
