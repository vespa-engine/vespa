# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled
                  COMMAND ant -q -buildfile ${CMAKE_CURRENT_SOURCE_DIR}/build.xml >>/dev/null
                  COMMAND ${CMAKE_COMMAND} -E touch ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled
                  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/build.xml ${CMAKE_CURRENT_SOURCE_DIR}/HelloWorld.java)
add_custom_target(lowercasing_test_java ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled)
