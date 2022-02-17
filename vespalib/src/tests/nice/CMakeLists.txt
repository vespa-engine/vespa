# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_nice_test_app TEST
    SOURCES
    nice_test.cpp
    DEPENDS
    vespalib
)
if(NOT CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
  vespa_add_test(NAME vespalib_nice_test_app COMMAND vespalib_nice_test_app)
endif()
