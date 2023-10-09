# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fbench_geturl_app
    SOURCES
    geturl.cpp
    OUTPUT_NAME vespa-fbench-geturl
    INSTALL bin
    DEPENDS
    fbench_util
    fbench_httpclient
)
