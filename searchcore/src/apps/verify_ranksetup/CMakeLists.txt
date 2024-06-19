# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespa_searchcore_verify_ranksetup
    SOURCES
    verify_ranksetup.cpp
    INSTALL lib64
    DEPENDS
    streamingvisitors
    searchcore_matching
    searchcore_documentmetastore
)
vespa_generate_config(vespa_searchcore_verify_ranksetup verify-ranksetup.def)

vespa_add_executable(searchcore_verify_ranksetup_app
    SOURCES
    verify_ranksetup_app.cpp
    OUTPUT_NAME vespa-verify-ranksetup-bin
    INSTALL bin
    DEPENDS
    vespa_searchcore_verify_ranksetup
)
