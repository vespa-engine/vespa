# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastlib_fast
    SOURCES
    INSTALL lib64
    DEPENDS
    fastlib_text
    fastlib_io
    fastlib_util
    fastlib_fast_testsuite
)
