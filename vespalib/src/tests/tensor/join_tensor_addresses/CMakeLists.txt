# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_join_tensor_addresses_test_app
    SOURCES
    join_tensor_addresses_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_join_tensor_addresses_test_app COMMAND vespalib_join_tensor_addresses_test_app)
