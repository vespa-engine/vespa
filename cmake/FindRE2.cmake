# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# There is no bundled FindRE2, so we supply our own minimal version to find
# the system RE2 library and header files.

find_path(RE2_INCLUDE_DIR
    NAMES re2/re2.h
)

find_library(RE2_LIBRARIES
    NAMES re2
)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(RE2
    FOUND_VAR RE2_FOUND
    REQUIRED_VARS RE2_LIBRARIES RE2_INCLUDE_DIR
)

