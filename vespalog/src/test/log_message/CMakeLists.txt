# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(GTest REQUIRED)
vespa_add_executable(vespalog_log_message_test_app TEST
    SOURCES
    log_message_test.cpp
    DEPENDS
    vespalog
    GTest::GTest
)
vespa_add_test(NAME vespalog_log_message_test_app COMMAND vespalog_log_message_test_app)
