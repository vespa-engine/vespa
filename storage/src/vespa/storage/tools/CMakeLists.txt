# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storage_getidealstate_app
    SOURCES
    getidealstate.cpp
    DEPENDS
    storage
)
vespa_add_executable(storage_generatedistributionbits_app
    SOURCES
    generatedistributionbits.cpp
    DEPENDS
    storage
)
vespa_add_executable(storage_storage-cmd_app
    SOURCES
    storage-cmd.cpp
    OUTPUT_NAME vespa-storage-cmd
    DEPENDS
)
