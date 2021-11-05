# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_memoryindex OBJECT
    SOURCES
    bundled_fields_context.cpp
    compact_words_store.cpp
    document_inverter.cpp
    document_inverter_collection.cpp
    document_inverter_context.cpp
    feature_store.cpp
    field_index.cpp
    field_index_base.cpp
    field_index_collection.cpp
    field_index_remover.cpp
    field_inverter.cpp
    invert_context.cpp
    invert_task.cpp
    memory_index.cpp
    ordered_field_index_inserter.cpp
    posting_iterator.cpp
    push_context.cpp
    push_task.cpp
    remove_task.cpp
    url_field_inverter.cpp
    word_store.cpp
    DEPENDS
)
