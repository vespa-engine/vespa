# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(eval_streamed OBJECT
    SOURCES
    streamed_value.cpp
    streamed_value_index.cpp
    streamed_value_utils.cpp
    streamed_value_builder.cpp
    streamed_value_builder_factory.cpp
    streamed_value_view.cpp
)
