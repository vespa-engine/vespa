# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in CppUnit (DEPRECATED).
# NOTE: All new tests should be written in gtest. Each test sub-module has a gtest runner.
vespa_add_executable(storage_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storage_teststorageserver
    storage_testvisiting
    storage_testcommon
    storage_testhostreporter
    storage_testdistributor
    storage_testpersistence
    storage_testfilestorage
    storage_teststatus
)

vespa_add_test(
    NAME storage_testrunner_app
    COMMAND storage_testrunner_app
)
