# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fsa_fsadump_app
    SOURCES
    fsadump.cpp
    OUTPUT_NAME vespa-fsadump
    INSTALL bin
    DEPENDS
    vespafsa
)
