# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_fastos OBJECT
    SOURCES
    file.cpp
    file_rw_ops.cpp
    linux_file.cpp
    unix_file.cpp
    DEPENDS
)
