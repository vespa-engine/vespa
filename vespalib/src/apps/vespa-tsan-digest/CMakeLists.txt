# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-tsan-digest_app
    SOURCES
    tsan_digest.cpp
    OUTPUT_NAME vespa-tsan-digest
    INSTALL bin
    DEPENDS
    vespalib
)
