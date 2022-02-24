# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_llvm_stress_test_app TEST
    SOURCES
    llvm_stress_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_llvm_stress_test_app NO_VALGRIND COMMAND eval_llvm_stress_test_app)
