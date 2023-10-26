# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_tensor_from_weighted_set_test_app TEST
    SOURCES
    tensor_from_weighted_set_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_tensor_from_weighted_set_test_app COMMAND searchlib_tensor_from_weighted_set_test_app)
