# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This file is included from vespaConfig.cmake and contains a list of
# targets that are imported from vespa.

add_library(searchlib SHARED IMPORTED)
set_target_properties(searchlib PROPERTIES IMPORTED_LOCATION ${VESPA_HOME}/lib64/libsearchlib.so INTERFACE_INCLUDE_DIRECTORIES ${VESPA_INCLUDE_DIR})
