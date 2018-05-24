# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(defaults_vespa-print-default_app
    SOURCES
    printdefault.cpp
    OUTPUT_NAME vespa-print-default
    INSTALL bin
    DEPENDS
    vespadefaults
)
set_source_files_properties(printdefault.cpp PROPERTIES COMPILE_FLAGS "${VTAG_DEFINES}")
