# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_data_slime OBJECT
    SOURCES
    array_traverser.cpp
    array_value.cpp
    basic_value.cpp
    basic_value_factory.cpp
    binary_format.cpp
    convenience.cpp
    cursor.cpp
    empty_value_factory.cpp
    external_data_value.cpp
    external_data_value_factory.cpp
    external_memory.cpp
    inject.cpp
    inserter.cpp
    inspector.cpp
    json_format.cpp
    named_symbol_inserter.cpp
    named_symbol_lookup.cpp
    nix_value.cpp
    object_traverser.cpp
    object_value.cpp
    resolved_symbol.cpp
    root_value.cpp
    slime.cpp
    strfmt.cpp
    symbol.cpp
    symbol_inserter.cpp
    symbol_lookup.cpp
    symbol_table.cpp
    type.cpp
    value.cpp
    value_factory.cpp
    DEPENDS
)
