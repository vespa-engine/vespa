# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_compile_cache_test_app TEST
    SOURCES
    compile_cache_test.cpp
    DEPENDS
    vespalib
    vespalib_vespalib_eval_llvm
)
vespa_add_test(NAME vespalib_compile_cache_test_app COMMAND vespalib_compile_cache_test_app)
