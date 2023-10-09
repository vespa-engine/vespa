# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_documentid_test_app TEST
    SOURCES
    documentid_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_documentid_test_app COMMAND document_documentid_test_app)
vespa_add_executable(document_documentid_benchmark_app
    SOURCES
    documentid_benchmark.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_documentid_benchmark_app COMMAND document_documentid_benchmark_app BENCHMARK)
