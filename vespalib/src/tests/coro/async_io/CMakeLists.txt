# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_async_io_test_app TEST
    SOURCES
    async_io_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
if(VESPA_USE_SANITIZER STREQUAL "thread")
   # Thread sanitizer is not happy with use of coroutines
   set(MAYBE_DISABLED_TEST "DISABLED")
endif()
vespa_add_test(NAME vespalib_async_io_test_app ${MAYBE_DISABLED_TEST} COMMAND vespalib_async_io_test_app)
