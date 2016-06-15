# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
add_custom_target(lowercasing_test_java ALL
  ant -q -buildfile ${CMAKE_CURRENT_SOURCE_DIR}/build.xml >>/dev/null
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/build.xml ${CMAKE_CURRENT_SOURCE_DIR}/HelloWorld.java
)
