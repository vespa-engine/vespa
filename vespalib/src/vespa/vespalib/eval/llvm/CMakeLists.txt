# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_eval_llvm
    SOURCES
    compile_cache.cpp
    compiled_function.cpp
    deinline_forest.cpp
    llvm_wrapper.cpp
    INSTALL lib64
    DEPENDS
    vespalib
    LLVM-3.4
)
