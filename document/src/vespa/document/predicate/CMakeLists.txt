# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_predicate OBJECT
    SOURCES
    predicate.cpp
    predicate_builder.cpp
    predicate_printer.cpp
    predicate_slime_builder.cpp
    predicate_slime_visitor.cpp
    DEPENDS
)
