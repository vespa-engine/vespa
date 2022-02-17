# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if(NOT DEFINED VESPA_USE_SANITIZER)
  vespa_add_test(NAME ann_benchmark_test NO_VALGRIND COMMAND ${PYTHON_EXECUTABLE} -m pytest WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} DEPENDS vespa_ann_benchmark)
endif()
