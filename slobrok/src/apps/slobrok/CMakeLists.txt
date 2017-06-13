# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_app
    SOURCES
    slobrok.cpp
    OUTPUT_NAME vespa-slobrok
    INSTALL bin
    DEPENDS
    slobrok_slobrokserver
)
