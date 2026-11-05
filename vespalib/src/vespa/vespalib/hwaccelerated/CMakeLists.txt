# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
  set(ACCEL_FILES "x64_generic.cpp" "avx2.cpp" "avx3.cpp" "avx3_dl.cpp")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64")
  set(ACCEL_FILES "neon.cpp" "neon_fp16_dotprod.cpp" "sve.cpp" "sve2.cpp")
else()
  message(FATAL_ERROR "Unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
endif()

vespa_add_library(vespa_hwaccelerated
    SOURCES
    fn_table.cpp
    float8_luts.cpp
    iaccelerated.cpp
    highway.cpp
    target_info.cpp
    ${ACCEL_FILES}
    DEPENDS
    INSTALL lib64
)
vespa_compile_flag_for(avx2.cpp              "-O3 -march=haswell")
vespa_compile_flag_for(avx3.cpp              "-O3 -march=skylake-avx512 -mprefer-vector-width=512")
vespa_compile_flag_for(avx3_dl.cpp           "-O3 -march=icelake-server -mprefer-vector-width=512")

vespa_compile_flag_for(neon.cpp              "-O3 -march=armv8.2-a")
vespa_compile_flag_for(neon_fp16_dotprod.cpp "-O3 -march=armv8.2-a+fp16+dotprod+crypto -mtune=neoverse-n1")
vespa_compile_flag_for(sve.cpp               "-O3 -march=armv8.2-a+sve -mtune=neoverse-n1")
vespa_compile_flag_for(sve2.cpp              "-O3 -march=armv8.2-a+sve2 -mtune=neoverse-n1")

set(BLA_VENDOR OpenBLAS)
vespa_add_target_package_dependency(vespa_hwaccelerated BLAS)
vespa_add_target_external_dependency(vespa_hwaccelerated hwy)
