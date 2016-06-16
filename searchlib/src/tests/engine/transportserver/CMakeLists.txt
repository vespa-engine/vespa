# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_transportserver_test_app TEST
    SOURCES
    transportserver_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(
    NAME searchlib_transportserver_test_app
    COMMAND searchlib_transportserver_test_app
    ENVIRONMENT "VESPA_LOG_TARGET=file:vlog.txt;VESPA_LOG_LEVEL=\"all -spam\""
)
