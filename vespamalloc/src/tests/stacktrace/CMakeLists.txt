# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_stacktrace_test_app TEST
    SOURCES
    stacktrace.cpp
    DEPENDS
)
vespa_add_test(
    NAME vespamalloc_stacktrace_test_app
    NO_VALGRIND COMMAND vespamalloc_stacktrace_test_app
    ENVIRONMENT "LD_PRELOAD=${CMAKE_CURRENT_BINARY_DIR}/../../vespamalloc/libvespamallocdst16.so"
    NO_VALGRIND)
