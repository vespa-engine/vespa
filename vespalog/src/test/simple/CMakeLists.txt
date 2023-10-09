# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_simple_test_app TEST
    SOURCES
    logtest.cpp
    DEPENDS
    vespalog
)
vespa_add_test(NAME vespalog_simple_test_app COMMAND vespalog_simple_test_app)
