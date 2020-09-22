# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(eval_packed_mappings_test_app TEST
    SOURCES
    packed_mappings_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest    
)
vespa_add_test(NAME eval_packed_mappings_test_app COMMAND eval_packed_mappings_test_app)

vespa_add_executable(eval_packed_mixed_test_app TEST
    SOURCES
    packed_mixed_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest    
)
vespa_add_test(NAME eval_packed_mixed_test_app COMMAND eval_packed_mixed_test_app)
