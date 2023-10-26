# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_simple_thread_bundle_test_app TEST
    SOURCES
    simple_thread_bundle_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_simple_thread_bundle_test_app COMMAND vespalib_simple_thread_bundle_test_app)
vespa_add_executable(vespalib_threading_speed_test_app
    SOURCES
    threading_speed_test.cpp
    DEPENDS
    vespalib
)
#vespa_add_test(NAME vespalib_threading_speed_test_app COMMAND vespalib_threading_speed_test_app)
