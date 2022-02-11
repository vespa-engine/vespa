# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_datastore OBJECT
    SOURCES
    array_store.cpp
    array_store_config.cpp
    buffer_type.cpp
    bufferstate.cpp
    compaction_strategy.cpp
    compact_buffer_candidates.cpp
    datastore.cpp
    datastorebase.cpp
    entryref.cpp
    entry_ref_filter.cpp
    fixed_size_hash_map.cpp
    large_array_buffer_type.cpp
    sharded_hash_map.cpp
    small_array_buffer_type.cpp
    unique_store.cpp
    unique_store_buffer_type.cpp
    unique_store_string_allocator.cpp
    DEPENDS
)
