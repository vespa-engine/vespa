# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_testurl_app
    SOURCES
    testurl.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_testurl_app COMMAND sh dotest.sh)
