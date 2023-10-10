# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_move_operation_limiter_test_app TEST
    SOURCES
    move_operation_limiter_test.cpp
    DEPENDS
    searchcore_server
)
vespa_add_test(NAME searchcore_move_operation_limiter_test_app COMMAND searchcore_move_operation_limiter_test_app)
