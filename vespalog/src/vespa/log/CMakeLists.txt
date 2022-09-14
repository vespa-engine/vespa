# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalog
    SOURCES
    exceptions.cpp
    loglevelnames.cpp
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
