# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell

include(vtag.cmake)

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
set(C_WARN_OPTS "-Winline -Wuninitialized -Werror -Wall -W -Wchar-subscripts -Wcomment -Wformat -Wparentheses -Wreturn-type -Wswitch -Wtrigraphs -Wunused -Wshadow -Wpointer-arith -Wcast-qual -Wcast-align -Wwrite-strings")

# Warnings that are specific to C++ compilation
# Note: this is not a union of C_WARN_OPTS, since CMAKE_CXX_FLAGS already includes CMAKE_C_FLAGS, which in turn includes C_WARN_OPTS transitively
set(CXX_SPECIFIC_WARN_OPTS "-Wsuggest-override -Wnon-virtual-dtor -Wformat-security")

# C and C++ compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -Og -fno-omit-frame-pointer ${C_WARN_OPTS} -fPIC ${VESPA_CXX_ABI_FLAGS} -DBOOST_DISABLE_ASSERTS ${VESPA_CPU_ARCH_FLAGS} -mtune=intel ${EXTRA_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CMAKE_C_FLAGS} ${CXX_SPECIFIC_WARN_OPTS} -std=c++1z -fvisibility-inlines-hidden -fdiagnostics-color=auto ${EXTRA_CXX_FLAGS}")

# Linker flags
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id -latomic -ldl -Wl,-E")
SET(CMAKE_EXE_LINKER_FLAGS  "${CMAKE_EXE_LINKER_FLAGS} -rdynamic" )

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
set (VESPA_LLVM_VERSION "3.9")
endif()
if(DEFINED VESPA_BOOST_LIB_SUFFIX)
else()
set (VESPA_BOOST_LIB_SUFFIX "-mt-d")
endif()

if(EXTRA_INCLUDE_DIRECTORY)
    include_directories(SYSTEM ${EXTRA_INCLUDE_DIRECTORY})
endif()
if(EXTRA_LINK_DIRECTORY)
    link_directories(${EXTRA_LINK_DIRECTORY})
endif()
if(CMAKE_BUILD_RPATH)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-rpath,${CMAKE_BUILD_RPATH}")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-rpath,${CMAKE_BUILD_RPATH}")
endif()

# Don't allow unresolved symbols in executables or shared libraries
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-undefined")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-undefined")
