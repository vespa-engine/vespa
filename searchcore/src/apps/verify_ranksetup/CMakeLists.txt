# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_verify_ranksetup
    SOURCES
    verify_ranksetup.cpp
    INSTALL lib64
    DEPENDS
    searchcore_fconfig
    searchcore_matching
    searchcore_documentmetastore
)
vespa_generate_config(searchcore_verify_ranksetup verify-ranksetup.def)

vespa_add_executable(searchcore_verify_ranksetup_app
    SOURCES
    verify_ranksetup_app.cpp
    OUTPUT_NAME vespa-verify-ranksetup-bin
    INSTALL bin
    DEPENDS
    searchcore_verify_ranksetup
)
