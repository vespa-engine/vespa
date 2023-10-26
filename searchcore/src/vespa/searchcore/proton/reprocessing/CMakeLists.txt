# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_reprocessing STATIC
    SOURCES
    attribute_reprocessing_initializer.cpp
    document_reprocessing_handler.cpp
    reprocess_documents_task.cpp
    reprocessingrunner.cpp
    DEPENDS
)
