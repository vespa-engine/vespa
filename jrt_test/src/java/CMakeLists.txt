# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
file(MAKE_DIRECTORY classes)
add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled
                   COMMAND ${CMAKE_CURRENT_BINARY_DIR}/../binref/compilejava -d classes *.java
                   COMMAND ${CMAKE_COMMAND} -E touch ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled
                   DEPENDS DummySlobrokService.java HelloWorld.java PollRPCServer.java SimpleServer.java
                   WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})
add_custom_target(jrt_test_java ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/java_code_compiled)
