# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_threads_test_app TEST
    SOURCES
    testthreads.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_threads_test_app COMMAND vespalog_threads_test_app vespa.log ENVIRONMENT "VESPA_LOG_TARGET=file:vespa.log")
