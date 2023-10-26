# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_testkit-mt_test_app TEST
    SOURCES
    testkit-mt_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_testkit-mt_test_app COMMAND vespalib_testkit-mt_test_app)
