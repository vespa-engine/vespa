# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_annotation_test_app TEST
    SOURCES
    annotation_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_annotation_test_app COMMAND document_annotation_test_app)
