# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(defaults_vespa-print-default_app
    SOURCES
    printdefault.cpp
    OUTPUT_NAME vespa-print-default-bin
    INSTALL bin
    DEPENDS
    vespadefaults
)
vespa_compile_flag_for(printdefault.cpp "${VTAG_DEFINES}")
