# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fbench_app
    SOURCES
    client.cpp
    fbench.cpp
    OUTPUT_NAME vespa-fbench
    INSTALL bin
    DEPENDS
    fbench_util
    fbench_httpclient
)
