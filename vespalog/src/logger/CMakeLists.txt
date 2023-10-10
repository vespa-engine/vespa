# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_vlogconv_app
    SOURCES
    logger.cpp
    llreader.cpp
    OUTPUT_NAME vespa-log-conv
    INSTALL bin
    DEPENDS
    vespalog
)
vespa_add_executable(vespalog_vlogreplay_app
    SOURCES
    logreplay.c
    OUTPUT_NAME vespa-log-replay
    INSTALL bin
    EXTERNAL_DEPENDS
    m
)
vespa_add_executable(vespalog_vespa-runserver_app
    SOURCES
    runserver.cpp
    llreader.cpp
    OUTPUT_NAME vespa-runserver
    INSTALL bin
    DEPENDS
    vespalog
)
