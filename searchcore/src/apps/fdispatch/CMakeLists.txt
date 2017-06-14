# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_fdispatch_app
    SOURCES
    fdispatch.cpp
    OUTPUT_NAME vespa-dispatch-bin
    INSTALL sbin
    DEPENDS
    searchcore_fdispatch_program
    searchcore_fdispatch_search
    searchcore_grouping
    searchcore_fdcommon
    searchcore_util
    searchcore_fconfig
    searchlib_searchlib_uca
)
