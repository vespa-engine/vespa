# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-drop-file-from-cache_app
    SOURCES
    drop_file_from_cache.cpp
    OUTPUT_NAME vespa-drop-file-from-cache
    INSTALL bin
    DEPENDS
    vespalib
)
