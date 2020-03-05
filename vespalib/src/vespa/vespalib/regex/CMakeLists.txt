# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_regex OBJECT
    SOURCES
    regex.cpp
    DEPENDS
)

find_package(RE2 REQUIRED)
# TODO can this be PRIVATE since we don't expose it transitively?
target_include_directories(vespalib_vespalib_regex PUBLIC ${RE2_INCLUDE_DIR})
