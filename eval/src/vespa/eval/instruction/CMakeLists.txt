# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(eval_instruction OBJECT
    SOURCES
    generic_concat.cpp
    generic_create.cpp
    generic_join.cpp
    generic_lambda.cpp
    generic_map.cpp
    generic_merge.cpp
    generic_peek.cpp
    generic_reduce.cpp
    generic_rename.cpp
    join_with_number_function.cpp
)
