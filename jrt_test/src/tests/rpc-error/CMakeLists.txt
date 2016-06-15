# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(jrt_test_test-errors_app
    SOURCES
    test-errors.cpp
    DEPENDS
)
vespa_add_test(NAME jrt_test_test-errors_app NO_VALGRIND COMMAND sh rpc-error_test.sh)
