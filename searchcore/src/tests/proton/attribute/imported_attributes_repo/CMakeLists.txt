# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_imported_attributes_repo_test_app TEST
    SOURCES
    imported_attributes_repo_test.cpp
    DEPENDS
    searchcore_attribute
)
vespa_add_test(NAME searchcore_imported_attributes_repo_test_app COMMAND searchcore_imported_attributes_repo_test_app)
