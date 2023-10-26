# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_alloc_test_app TEST
    SOURCES
    alloc_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_alloc_test_app NO_VALGRIND COMMAND vespalib_alloc_test_app)
vespa_add_executable(vespalib_allocate_and_core_app
    SOURCES
    allocate_and_core.cpp
    DEPENDS
    vespalib
)
