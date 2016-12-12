# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_array_store_config_test_app TEST
    SOURCES
    array_store_config_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_array_store_config_test_app COMMAND searchlib_array_store_config_test_app)
