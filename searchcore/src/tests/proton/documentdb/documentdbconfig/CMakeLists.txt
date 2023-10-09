# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentdbconfig_test_app TEST
    SOURCES
    documentdbconfig_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
)
vespa_add_test(NAME searchcore_documentdbconfig_test_app COMMAND searchcore_documentdbconfig_test_app)
