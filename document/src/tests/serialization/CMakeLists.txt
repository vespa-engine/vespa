# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_vespadocumentserializer_test_app TEST
    SOURCES
    vespadocumentserializer_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_vespadocumentserializer_test_app COMMAND document_vespadocumentserializer_test_app)
vespa_add_executable(document_annotationserializer_test_app TEST
    SOURCES
    annotationserializer_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_annotationserializer_test_app COMMAND document_annotationserializer_test_app)
