# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_detached_test_app TEST
    SOURCES
    detached_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_detached_test_app COMMAND vespalib_detached_test_app)
