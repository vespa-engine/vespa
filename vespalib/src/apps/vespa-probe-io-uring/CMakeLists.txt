# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-probe-io-uring_app
    SOURCES
    probe_io_uring.cpp
    OUTPUT_NAME vespa-probe-io-uring
    INSTALL bin
    DEPENDS
    vespalib
)
