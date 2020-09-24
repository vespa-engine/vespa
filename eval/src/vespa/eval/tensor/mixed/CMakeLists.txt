# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(eval_tensor_mixed OBJECT
    SOURCES
    packed_labels.cpp
    packed_mappings.cpp
    packed_mappings_builder.cpp
    packed_mixed_tensor_builder_factory.cpp
    packed_mixed_tensor.cpp
    packed_mixed_tensor_builder.cpp
)
