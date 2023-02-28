# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fbench_httpclient STATIC
    SOURCES
    httpclient.cpp
    DEPENDS
    fbench_util
)
