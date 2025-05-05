# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell

if (EXISTS ${CMAKE_CURRENT_LIST_DIR}/vtag.cmake)
  include(${CMAKE_CURRENT_LIST_DIR}/vtag.cmake)
endif()

if(VESPA_USER)
else()
  set(VESPA_USER "vespa")
endif()

if(NOT DEFINED VESPA_GROUP)
  set(VESPA_GROUP "vespa")
endif()

if(VESPA_UNPRIVILEGED)
else()
  set(VESPA_UNPRIVILEGED "no")
endif()
