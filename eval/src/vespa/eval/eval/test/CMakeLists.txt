# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_eval_test OBJECT
    SOURCES
    cell_type_space.cpp
    eval_fixture.cpp
    eval_onnx.cpp
    eval_spec.cpp
    gen_spec.cpp
    reference_evaluation.cpp
    reference_operations.cpp
    test_io.cpp
    value_compare.cpp
)
