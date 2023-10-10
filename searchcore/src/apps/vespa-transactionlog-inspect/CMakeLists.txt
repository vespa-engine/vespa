# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa-transactionlog-inspect_app
    SOURCES
    vespa-transactionlog-inspect.cpp
    OUTPUT_NAME vespa-transactionlog-inspect-bin
    INSTALL bin
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_pcommon
)
