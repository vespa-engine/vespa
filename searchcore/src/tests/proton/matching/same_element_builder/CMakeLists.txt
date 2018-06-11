# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_same_element_builder_test_app TEST
    SOURCES
    same_element_builder_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_same_element_builder_test_app COMMAND searchcore_same_element_builder_test_app)
