# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(vespalib_assert_test_app TEST
    SOURCES
    assert_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_assert_test_app COMMAND vespalib_assert_test_app)

vespa_add_executable(vespalib_asserter_app TEST
    SOURCES
    asserter.cpp
    DEPENDS
    vespalib
)
