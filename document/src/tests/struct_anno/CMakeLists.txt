# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_struct_anno_test_app TEST
    SOURCES
    struct_anno_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_struct_anno_test_app COMMAND document_struct_anno_test_app)
