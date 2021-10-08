# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_memoryindex OBJECT
    SOURCES
    compact_words_store.cpp
    document_inverter.cpp
    feature_store.cpp
    field_index.cpp
    field_index_base.cpp
    field_index_collection.cpp
    field_index_remover.cpp
    field_inverter.cpp
    memory_index.cpp
    ordered_field_index_inserter.cpp
    posting_iterator.cpp
    url_field_inverter.cpp
    word_store.cpp
    DEPENDS
)
