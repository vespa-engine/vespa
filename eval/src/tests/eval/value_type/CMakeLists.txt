# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_value_type_test_app TEST
    SOURCES
    value_type_test.cpp
    DEPENDS
    eval
)
vespa_add_test(NAME vespalib_value_type_test_app NO_VALGRIND COMMAND vespalib_value_type_test_app)
