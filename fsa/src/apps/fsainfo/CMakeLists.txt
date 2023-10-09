# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fsa_fsainfo_app
    SOURCES
    fsainfo.cpp
    OUTPUT_NAME vespa-fsainfo
    INSTALL bin
    DEPENDS
    vespafsa
)
