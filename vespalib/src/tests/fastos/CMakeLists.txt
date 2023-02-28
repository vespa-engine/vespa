# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_file_test_app TEST
    SOURCES
    file_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME fastos_file_test_app COMMAND fastos_file_test_app)
