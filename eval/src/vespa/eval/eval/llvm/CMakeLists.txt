# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_eval_llvm OBJECT
    SOURCES
    addr_to_symbol.cpp
    compile_cache.cpp
    compiled_function.cpp
    deinline_forest.cpp
    llvm_wrapper.cpp
)
if (APPLE)
    if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang")
        set_source_files_properties(addr_to_symbol.cpp PROPERTIES COMPILE_OPTIONS "-Wno-error=deprecated-declarations")
    endif()
endif()
