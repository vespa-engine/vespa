# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell

include(${CMAKE_CURRENT_LIST_DIR}/vtag.cmake)

if (VESPA_USE_SANITIZER)
    if (VESPA_USE_SANITIZER STREQUAL "address" OR VESPA_USE_SANITIZER STREQUAL "thread")
        message("-- Instrumenting code using ${VESPA_USE_SANITIZER} sanitizer")
    else()
        message(FATAL_ERROR "Unsupported sanitizer option '${VESPA_USE_SANITIZER}'. Supported: 'address' or 'thread'")
    endif()
endif()

# Build options
# Whether to build unit tests as part of the 'all' target
set(EXCLUDE_TESTS_FROM_ALL FALSE CACHE BOOL "If TRUE, do not build tests as part of the 'all' target")

# Whether to run unit tests via valgrind
set(VALGRIND_UNIT_TESTS FALSE CACHE BOOL "If TRUE, run unit tests via valgrind")

# Whether to run tests marked as benchmark as part of the test runs
set(RUN_BENCHMARKS FALSE CACHE BOOL "If TRUE, benchmarks are run together with the other tests")

# Whether to run tests marked as benchmark as part of the test runs
set(AUTORUN_UNIT_TESTS FALSE CACHE BOOL "If TRUE, tests will be run immediately after linking the test executable")

# Warnings
set(C_WARN_OPTS "-Wuninitialized -Werror -Wall -W -Wchar-subscripts -Wcomment -Wformat -Wparentheses -Wreturn-type -Wswitch -Wtrigraphs -Wunused -Wshadow -Wpointer-arith -Wcast-qual -Wcast-align -Wwrite-strings")
if (VESPA_USE_SANITIZER)
    # Instrumenting code changes binary size, which triggers inlining warnings that
    # don't happen during normal, non-instrumented compilation.
else()
    set(C_WARN_OPTS "-Winline ${C_WARN_OPTS}")
endif()

# Warnings that are specific to C++ compilation
# Note: this is not a union of C_WARN_OPTS, since CMAKE_CXX_FLAGS already includes CMAKE_C_FLAGS, which in turn includes C_WARN_OPTS transitively
if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang" OR "${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
  set(CXX_SPECIFIC_WARN_OPTS "-Wnon-virtual-dtor -Wformat-security -Wno-overloaded-virtual")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-delete-null-pointer-checks -fsized-deallocation")
  if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(VESPA_ATOMIC_LIB "")
    set(VESPA_GCC_LIB "")
    set(VESPA_STDCXX_FS_LIB "")
  else()
    set(VESPA_ATOMIC_LIB "atomic")
    if (CMAKE_CXX_COMPILER_VERSION VERSION_LESS 8.0)
      set(VESPA_GCC_LIB "gcc")
      set(VESPA_STDCXX_FS_LIB "stdc++fs")
    else()
      set(VESPA_GCC_LIB "")
      set(VESPA_STDCXX_FS_LIB "")
    endif()
  endif()
else()
  set(CXX_SPECIFIC_WARN_OPTS "-Wnoexcept -Wsuggest-override -Wnon-virtual-dtor -Wformat-security")
  if(VESPA_OS_DISTRO_COMBINED STREQUAL "centos 8" OR
      (VESPA_OS_DISTRO STREQUAL "rhel" AND
	VESPA_OS_DISTRO_VERSION VERSION_GREATER_EQUAL "8" AND
	VESPA_OS_DISTRO_VERSION VERSION_LESS "9"))
    set(VESPA_ATOMIC_LIB "")
  else()
    set(VESPA_ATOMIC_LIB "atomic")
  endif()
  set(VESPA_GCC_LIB "gcc")
  set(VESPA_STDCXX_FS_LIB "stdc++fs")
endif()

if(VESPA_OS_DISTRO_COMBINED STREQUAL "debian 10")
  unset(VESPA_XXHASH_DEFINE)
else()
  set(VESPA_XXHASH_DEFINE "-DXXH_INLINE_ALL")
endif()

# C and C++ compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -O3 -fno-omit-frame-pointer ${C_WARN_OPTS} -fPIC ${VESPA_CXX_ABI_FLAGS} ${VESPA_XXHASH_DEFINE} -DBOOST_DISABLE_ASSERTS ${VESPA_CPU_ARCH_FLAGS} ${EXTRA_C_FLAGS}")
# AddressSanitizer/ThreadSanitizer work for both GCC and Clang
if (VESPA_USE_SANITIZER)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=${VESPA_USE_SANITIZER}")
endif()
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CMAKE_C_FLAGS} ${CXX_SPECIFIC_WARN_OPTS} -std=c++2a -fdiagnostics-color=auto ${EXTRA_CXX_FLAGS}")
if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ")
else()
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility-inlines-hidden ")
endif()

# Linker flags
if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
  if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang" OR "${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -ldl")
  else()
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -latomic -ldl")
  endif()
else()
  if(VESPA_ATOMIC_LIB STREQUAL "")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id -ldl -Wl,-E")
  else()
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id -latomic -ldl -Wl,-E")
  endif()
endif()
SET(CMAKE_EXE_LINKER_FLAGS  "${CMAKE_EXE_LINKER_FLAGS} -rdynamic" )

message("-- CMAKE_SHARED_LINKER_FLAGS is ${CMAKE_SHARED_LINKER_FLAGS}")

# Use C++ 17
# TODO renable when cmake 3.8 is out.
# set(CMAKE_CXX_STANDARD 17)

# Always build shared libs if not explicitly specified
set(BUILD_SHARED_LIBS ON)

set(CMAKE_THREAD_PREFER_PTHREAD TRUE)

# Default RPATH settings for CMake 3.4:
# For non-installed targets, reference external libraries using an RPATH into the build tree.
# For installed targets, reference external libraries using INSTALL_RPATH (i.e. /opt/vespa/lib64 on ylinux)
set(CMAKE_CMAKE_SKIP_BUILD_RPATH FALSE)
set(CMAKE_BUILD_WITH_INSTALL_RPATH FALSE)
set(CMAKE_INSTALL_RPATH_USE_LINK_PATH FALSE)

# OS X Stuff
if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(MACOSX_RPATH ON)

    if(__COMPILER_GNU)
        SET(CMAKE_INCLUDE_SYSTEM_FLAG_C "-isystem ")
        SET(CMAKE_INCLUDE_SYSTEM_FLAG_CXX "-isystem ")
    endif()
endif()

# Find ccache and use it if it is found
find_program(CCACHE_EXECUTABLE ccache)
if(CCACHE_EXECUTABLE)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE ${CCACHE_EXECUTABLE})
    set_property(GLOBAL PROPERTY RULE_LAUNCH_LINK ${CCACHE_EXECUTABLE})
endif()

# Check for valgrind and set flags
find_program(VALGRIND_EXECUTABLE valgrind)
if(VALGRIND_EXECUTABLE)
    set(VALGRIND_SUPPRESSIONS_FILE "${PROJECT_SOURCE_DIR}/valgrind-suppressions.txt")
    set(VALGRIND_OPTIONS "--leak-check=yes --error-exitcode=1 --run-libc-freeres=no --track-origins=yes --suppressions=${VALGRIND_SUPPRESSIONS_FILE}")
    set(VALGRIND_COMMAND "${VALGRIND_EXECUTABLE} ${VALGRIND_OPTIONS}")
endif()

if(VESPA_LLVM_VERSION)
else()
set (VESPA_LLVM_VERSION "6.0")
endif()

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
  set(VESPA_LLVM_LIB "LLVM")
  set(VESPA_GLIBC_RT_LIB "")
else()
  set(VESPA_LLVM_LIB "LLVM-${VESPA_LLVM_VERSION}")
  set(VESPA_GLIBC_RT_LIB "rt")
endif()

if(VESPA_USER)
else()
  set(VESPA_USER "vespa")
endif()

if(VESPA_UNPRIVILEGED)
else()
  set(VESPA_UNPRIVILEGED "no")
endif()

if(EXTRA_INCLUDE_DIRECTORY)
    include_directories(SYSTEM ${EXTRA_INCLUDE_DIRECTORY})
endif()
if(EXTRA_LINK_DIRECTORY)
    link_directories(${EXTRA_LINK_DIRECTORY})
endif()

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
else()
if(NOT VESPA_USE_SANITIZER OR NOT "${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang")
  # Don't allow unresolved symbols in shared libraries
  set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-undefined")
endif()
# Don't allow unresolved symbols in executables
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-undefined")

# Enable GTest unit tests in shared libraries
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-as-needed")
endif()
