# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_tensor_address_test_app TEST
    SOURCES
    tensor_address_test.cpp
    DEPENDS
    eval
)
vespa_add_test(NAME vespalib_tensor_address_test_app COMMAND vespalib_tensor_address_test_app)
