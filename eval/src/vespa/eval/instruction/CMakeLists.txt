# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(eval_instruction OBJECT
    SOURCES
    generic_concat
    generic_create
    generic_join
    generic_peek
    generic_reduce
    generic_map
    generic_merge
    generic_rename
)
