# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_function_speed_test_app
    SOURCES
    function_speed_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_function_speed_test_app NO_VALGRIND COMMAND vespalib_function_speed_test_app)
