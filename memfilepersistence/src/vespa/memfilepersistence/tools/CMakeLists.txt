# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_tools OBJECT
    SOURCES
    dumpslotfile.cpp
    vdsdisktool.cpp
    DEPENDS
)
vespa_add_executable(memfilepersistence_dumpslotfile_app
    SOURCES
    dumpslotfileapp.cpp
    OUTPUT_NAME vespa-dump-slotfile
    INSTALL bin
    DEPENDS
    memfilepersistence
)
vespa_add_executable(memfilepersistence_vdsdisktool_app
    SOURCES
    vdsdiskapp.cpp
    OUTPUT_NAME vespa-vds-disktool-bin
    INSTALL bin
    DEPENDS
    memfilepersistence
)
