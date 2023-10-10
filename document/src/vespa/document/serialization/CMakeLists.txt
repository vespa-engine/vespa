# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_serialization OBJECT
    SOURCES
    annotationdeserializer.cpp
    annotationserializer.cpp
    slime_output_to_vector.cpp
    vespadocumentserializer.cpp
    vespadocumentdeserializer.cpp
    DEPENDS
)
