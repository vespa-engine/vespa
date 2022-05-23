# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_benchmark_test_app
    SOURCES
    benchmark.cpp
    testbase.cpp
    DEPENDS
    vespalib
    EXTERNAL_DEPENDS
    ${VESPA_GLIBC_RT_LIB}
)
vespa_add_test(NAME vespalib_benchmark_test NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/benchmark_test.sh BENCHMARK
               DEPENDS vespalib_benchmark_test_app)
