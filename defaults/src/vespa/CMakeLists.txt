# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespadefaults
    SOURCES
    defaults.cpp
    INSTALL lib64
    DEPENDS
)

function(vespa_configure_config_h)
  set(VESPA_USE_ADDRESS_SANITIZER False)
  set(VESPA_USE_THREAD_SANITIZER False)
  set(VESPA_USE_UNDEFINED_SANITIZER False)
  set(VESPA_HAS_IO_URING False)
  set(VESPA_HAS_EPOLL False)
  if(VESPA_USE_SANITIZER STREQUAL "address")
    set(VESPA_USE_ADDRESS_SANITIZER True)
  elseif(VESPA_USE_SANITIZER STREQUAL "thread")
    set(VESPA_USE_THREAD_SANITIZER True)
  elseif(VESPA_USE_SANITIZER STREQUAL "undefined")
    set(VESPA_USE_UNDEFINED_SANITIZER True)
  endif()
  if(NOT APPLE)
    set(VESPA_HAS_EPOLL True)
    set(VESPA_HAS_IO_URING True)
  endif()
  set(DO_NOT_EDIT_MESSAGE "This file is generated. Do not edit.")
  configure_file(config.h.in config.h @ONLY)
endfunction()

vespa_configure_config_h()

install(FILES defaults.h config.h DESTINATION include/vespa)
