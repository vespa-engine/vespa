# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fbench_authority_test_app TEST
    SOURCES
    authority_test.cpp
    DEPENDS
    fbench_util
    vespalib
    GTest::GTest
)
vespa_add_test(NAME fbench_authority_test_app COMMAND fbench_authority_test_app)
