# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalog
    SOURCES
    exceptions.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/loglevelnames.cpp
    log.cpp
    bufferedlogger.cpp
    log-target-fd.cpp
    log-target-file.cpp
    log-target.cpp
    log_message.cpp
    lock.cpp
    internal.cpp
    control-file.cpp
    component.cpp
    llparser.cpp
    log-assert.cpp
    reject-filter.cpp
    INSTALL lib64
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/loglevelnames.cpp
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/mknm.pl < ${CMAKE_CURRENT_SOURCE_DIR}/log.h > lln.NEW && mv lln.NEW ${CMAKE_CURRENT_BINARY_DIR}/loglevelnames.cpp
    MAIN_DEPENDENCY log.h
    DEPENDENCIES mknm.pl
)
    
