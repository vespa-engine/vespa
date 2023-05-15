# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_flushengine_test_app TEST
    SOURCES
    flushengine_test.cpp
    DEPENDS
    searchcore_flushengine
    searchcore_pcommon
    searchcore_test
)
vespa_add_test(
    NAME searchcore_flushengine_test_app
    COMMAND searchcore_flushengine_test_app
    ENVIRONMENT "VESPA_LOG_LEVEL=all"
)
