# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_intrinsic_blueprint_adapter_test_app TEST
    SOURCES
    intrinsic_blueprint_adapter_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_intrinsic_blueprint_adapter_test_app COMMAND searchlib_intrinsic_blueprint_adapter_test_app)
