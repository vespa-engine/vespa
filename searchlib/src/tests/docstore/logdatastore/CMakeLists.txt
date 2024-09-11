# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_logdatastore_test_app TEST
    SOURCES
    logdatastore_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_logdatastore_test_app COMMAND searchlib_logdatastore_test_app ENVIRONMENT VESPA_LOG_TARGET=file:vlog2.txt)
