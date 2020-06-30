# Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(GTest REQUIRED)
vespa_add_executable(vespalib_crypto_crypto_test_app TEST
    SOURCES
    crypto_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_crypto_crypto_test_app COMMAND vespalib_crypto_crypto_test_app)

