# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_multi_test_app
    SOURCES
    multi.cpp
    DEPENDS
)
vespa_add_test(NAME slobrok_multi_test_app NO_VALGRIND COMMAND sh multi_test.sh)
