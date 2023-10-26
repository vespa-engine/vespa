# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_compile_cache_test_app TEST
    SOURCES
    compile_cache_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_compile_cache_test_app COMMAND eval_compile_cache_test_app)
