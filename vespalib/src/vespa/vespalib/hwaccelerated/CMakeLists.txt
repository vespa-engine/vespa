# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
  set(ACCEL_FILES "x64_generic.cpp" "avx2.cpp" "avx3.cpp" "avx3_dl.cpp")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
  set(ACCEL_FILES "neon.cpp" "neon_fp16_dotprod.cpp")
else()
  message(FATAL_ERROR "Unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
endif()

vespa_add_library(vespa_hwaccelerated
    SOURCES
    iaccelerated.cpp
    ${ACCEL_FILES}
    DEPENDS
    INSTALL lib64
)
set_source_files_properties(avx2.cpp              PROPERTIES COMPILE_FLAGS "-O3 -march=haswell")
set_source_files_properties(avx3.cpp              PROPERTIES COMPILE_FLAGS "-O3 -march=skylake-avx512 -mprefer-vector-width=512")
set_source_files_properties(avx3_dl.cpp           PROPERTIES COMPILE_FLAGS "-O3 -march=icelake-server -mprefer-vector-width=512")

set_source_files_properties(neon.cpp              PROPERTIES COMPILE_FLAGS "-O3 -march=armv8.2-a")
set_source_files_properties(neon_fp16_dotprod.cpp PROPERTIES COMPILE_FLAGS "-O3 -march=armv8.2-a+fp16+dotprod+crypto -mtune=neoverse-n1")

set(BLA_VENDOR OpenBLAS)
vespa_add_target_package_dependency(vespa_hwaccelerated BLAS)
