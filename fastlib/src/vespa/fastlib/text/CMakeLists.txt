# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastlib_text
    SOURCES
    unicodeutil.cpp
    wordfolder.cpp
    normwordfolder.cpp
    INSTALL lib64
    DEPENDS
)
