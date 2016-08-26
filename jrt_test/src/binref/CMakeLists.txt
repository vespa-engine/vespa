# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

configure_file(compilejava.in compilejava @ONLY)
configure_file(runjava.in runjava @ONLY)
configure_file(env.sh.in env.sh @ONLY)

if (NOT ${CMAKE_CURRENT_BINARY_DIR} STREQUAL ${CMAKE_CURRENT_SOURCE_DIR})
    file(COPY sbcmd simpleserver slobrok DESTINATION ${CMAKE_CURRENT_BINARY_DIR})

    # Workaround for CLion (https://youtrack.jetbrains.com/issue/CPP-7462)
    if (NOT EXISTS ${PROJECT_BINARY_DIR}/slobrok/src/apps/sbcmd/sbcmd)
       execute_process(COMMAND mkdir -p ${PROJECT_BINARY_DIR}/slobrok/src/apps/sbcmd)
       execute_process(COMMAND touch -t 197001010000 ${PROJECT_BINARY_DIR}/slobrok/src/apps/sbcmd/sbcmd)
    endif()
    if (NOT EXISTS ${PROJECT_BINARY_DIR}/slobrok/src/apps/slobrok/slobrok)
       execute_process(COMMAND mkdir -p ${PROJECT_BINARY_DIR}/slobrok/src/apps/slobrok)
       execute_process(COMMAND touch -t 197001010000 ${PROJECT_BINARY_DIR}/slobrok/src/apps/slobrok/slobrok)
    endif()
    if (NOT EXISTS ${PROJECT_BINARY_DIR}/jrt_test/src/jrt-test/simpleserver/jrt_test_simpleserver_app)
       execute_process(COMMAND mkdir -p f ${PROJECT_BINARY_DIR}/jrt_test/src/jrt-test/simpleserver)
       execute_process(COMMAND touch -t 197001010000 ${PROJECT_BINARY_DIR}/jrt_test/src/jrt-test/simpleserver/jrt_test_simpleserver_app)
    endif()
endif()
