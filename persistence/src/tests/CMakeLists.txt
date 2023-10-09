# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(persistence_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    persistence_testspi
)

vespa_add_test(
    NAME persistence_testrunner_app
    COMMAND persistence_testrunner_app
)
