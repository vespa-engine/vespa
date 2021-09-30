# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespa-tensor-instructions-benchmark TEST
    SOURCES
    instruction_benchmark.cpp
    OUTPUT_NAME vespa-tensor-instructions-benchmark
    INSTALL bin
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_tensor_instruction_test COMMAND vespa-tensor-instructions-benchmark --smoke-test)
