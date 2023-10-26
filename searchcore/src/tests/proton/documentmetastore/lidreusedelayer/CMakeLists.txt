# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_lidreusedelayer_test_app TEST
    SOURCES
    lidreusedelayer_test.cpp
    DEPENDS
    searchcore_server
    searchcore_documentmetastore
    searchcore_test
)
vespa_add_test(NAME searchcore_lidreusedelayer_test_app COMMAND searchcore_lidreusedelayer_test_app)
