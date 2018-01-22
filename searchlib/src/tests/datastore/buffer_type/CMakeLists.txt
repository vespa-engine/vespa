# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_buffer_type_test_app TEST
    SOURCES
    buffer_type_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_buffer_type_test_app COMMAND searchlib_buffer_type_test_app)
