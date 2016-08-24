# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

configure_file(compilejava.in compilejava @ONLY)
configure_file(runjava.in runjava @ONLY)
configure_file(env.sh.in env.sh @ONLY)

if (NOT ${CMAKE_CURRENT_BINARY_DIR} STREQUAL ${CMAKE_CURRENT_SOURCE_DIR})
    file(COPY sbcmd simpleserver slobrok DESTINATION ${CMAKE_CURRENT_BINARY_DIR})
endif()
