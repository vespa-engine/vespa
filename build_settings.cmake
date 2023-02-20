# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell

include(${CMAKE_CURRENT_LIST_DIR}/vtag.cmake)

if (VESPA_USE_SANITIZER)
    if (VESPA_USE_SANITIZER STREQUAL "address" OR VESPA_USE_SANITIZER STREQUAL "thread" OR VESPA_USE_SANITIZER STREQUAL "undefined")
        message("-- Instrumenting code using ${VESPA_USE_SANITIZER} sanitizer")
    else()
        message(FATAL_ERROR "Unsupported sanitizer option '${VESPA_USE_SANITIZER}'. Supported: 'address', 'thread' or 'undefined'")
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
if (VESPA_USE_SANITIZER OR VESPA_DISABLE_INLINE_WARNINGS)
    # Instrumenting code changes binary size, which triggers inlining warnings that
    # don't happen during normal, non-instrumented compilation.
else()
    set(C_WARN_OPTS "-Winline ${C_WARN_OPTS}")
endif()
if (VESPA_USE_SANITIZER)
  if (VESPA_USE_SANITIZER STREQUAL "address" AND CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 12.0)
    # Turn off maybe uninitialized and restrict warnings when compiling with
    # address sanitizer on gcc 12 or newer.
    set(C_WARN_OPTS "${C_WARN_OPTS} -Wno-maybe-uninitialized -Wno-restrict")
  endif()
  if (VESPA_USE_SANITIZER STREQUAL "thread" AND CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 12.0)
    # Turn off warning about std::atomic_thread_fence not being supported by
    # address sanitizer.
    set(C_WARN_OPTS "${C_WARN_OPTS} -Wno-tsan")
  endif()
endif()

# Warnings that are specific to C++ compilation
# Note: this is not a union of C_WARN_OPTS, since CMAKE_CXX_FLAGS already includes CMAKE_C_FLAGS, which in turn includes C_WARN_OPTS transitively
set(VESPA_ATOMIC_LIB "atomic")
if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang" OR "${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
  set(CXX_SPECIFIC_WARN_OPTS "-Wnon-virtual-dtor -Wformat-security -Wno-overloaded-virtual")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-delete-null-pointer-checks -fsized-deallocation")
  if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(VESPA_ATOMIC_LIB "")
    set(VESPA_GCC_LIB "")
    set(VESPA_STDCXX_FS_LIB "")
  else()
    if (CMAKE_CXX_COMPILER_VERSION VERSION_LESS 8.0)
      set(VESPA_GCC_LIB "gcc")
      set(VESPA_STDCXX_FS_LIB "stdc++fs")
    else()
      set(VESPA_GCC_LIB "")
      set(VESPA_STDCXX_FS_LIB "")
    endif()
  endif()
else()
  set(CXX_SPECIFIC_WARN_OPTS "-Wnoexcept -Wsuggest-override -Wnon-virtual-dtor -Wformat-security -Wmismatched-tags")
  set(VESPA_GCC_LIB "gcc")
  set(VESPA_STDCXX_FS_LIB "stdc++fs")
endif()

# Detect uring shared library.
if(EXISTS "/usr/${CMAKE_INSTALL_LIBDIR}/liburing.so")
  set(VESPA_URING_LIB "uring")
  message("-- liburing found")
else()
  set(VESPA_URING_LIB "")
  message("-- liburing not found")
endif()

if(VESPA_OS_DISTRO_COMBINED STREQUAL "debian 10")
  unset(VESPA_XXHASH_DEFINE)
else()
  set(VESPA_XXHASH_DEFINE "-DXXH_INLINE_ALL")
endif()

# Disable dangling reference and overloaded virtual warnings when using gcc 13
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
  if (CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL "13")
    set(CXX_SPECIFIC_WARN_OPTS "${CXX_SPECIFIC_WARN_OPTS} -Wno-dangling-reference -Wno-overloaded-virtual")
  endif()
endif()

if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND VESPA_USE_LTO)
  # Enable lto
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -flto=auto -ffat-lto-objects")
endif()

# C and C++ compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -O3 -fno-omit-frame-pointer ${C_WARN_OPTS} -fPIC ${VESPA_CXX_ABI_FLAGS} ${VESPA_XXHASH_DEFINE} -DBOOST_DISABLE_ASSERTS ${VESPA_CPU_ARCH_FLAGS} ${EXTRA_C_FLAGS}")
# AddressSanitizer/ThreadSanitizer work for both GCC and Clang
if (VESPA_USE_SANITIZER)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=${VESPA_USE_SANITIZER}")
    if (VESPA_USE_SANITIZER STREQUAL "undefined")
        # Many false positives when checking vptr due to limited visibility
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-sanitize=vptr")
    endif()
endif()
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CMAKE_C_FLAGS} ${CXX_SPECIFIC_WARN_OPTS} -std=c++2a -fdiagnostics-color=auto ${EXTRA_CXX_FLAGS}")
if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ")
else()
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility-inlines-hidden ")
endif()
if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_LESS 11.0)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fcoroutines")
endif()
if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DGOOGLE_PROTOBUF_NO_RDTSC")
  if(CMAKE_CXX_COMPILER_ID STREQUAL "Clang" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 15.0)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DBOOST_NO_CXX98_FUNCTION_BASE")
  endif()
endif()

# Hardening
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND VESPA_USE_HARDENING)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wp,-D_FORTIFY_SOURCE=2 -Wp,-D_GLIBCXX_ASSERTIONS -fstack-protector-strong -fasynchronous-unwind-tables -fstack-clash-protection -fcf-protection")
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
# Automatically set sanitizer suppressions file and arguments for unit tests
if(VESPA_USE_SANITIZER)
    if(VESPA_USE_SANITIZER STREQUAL "thread")
        set(VESPA_SANITIZER_SUPPRESSIONS_FILE "${PROJECT_SOURCE_DIR}/tsan-suppressions.txt")
        # Maximize the amount of history we can track, including mutex order inversion histories
        set(VESPA_SANITIZER_ENV "TSAN_OPTIONS=suppressions=${VESPA_SANITIZER_SUPPRESSIONS_FILE} history_size=7 detect_deadlocks=1 second_deadlock_stack=1")
    endif()
endif()
# Dump stack when finding issues in unit tests using undefined sanitizer
if(VESPA_USE_SANITIZER)
    if(VESPA_USE_SANITIZER STREQUAL "undefined")
        set(VESPA_SANITIZER_ENV "UBSAN_OPTIONS=print_stacktrace=1")
    endif()
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

if(NOT DEFINED VESPA_GROUP)
  set(VESPA_GROUP "vespa")
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
  set(VESPA_DISALLOW_UNRESOLVED_SYMBOLS_IN_SHARED_LIBRARIES "-Wl,--no-undefined")
endif()
# Don't allow unresolved symbols in executables
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-undefined")

# Enable GTest unit tests in shared libraries
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-as-needed")
endif()
