# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_predicate_test_app TEST
    SOURCES
    predicate_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_predicate_test_app COMMAND document_predicate_test_app)
vespa_add_executable(document_predicate_builder_test_app TEST
    SOURCES
    predicate_builder_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_predicate_builder_test_app COMMAND document_predicate_builder_test_app)
vespa_add_executable(document_predicate_printer_test_app TEST
    SOURCES
    predicate_printer_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_predicate_printer_test_app COMMAND document_predicate_printer_test_app)
