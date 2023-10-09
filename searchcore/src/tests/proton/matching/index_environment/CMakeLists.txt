# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_index_environment_test_app TEST
    SOURCES
    index_environment_test.cpp
    DEPENDS
    searchcore_matching
    searchcore_documentmetastore
)
vespa_add_test(NAME searchcore_index_environment_test_app COMMAND searchcore_index_environment_test_app)
