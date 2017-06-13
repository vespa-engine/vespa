# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_component OBJECT
    SOURCES
    storagecomponentregisterimpl.cpp
    servicelayercomponentregisterimpl.cpp
    distributorcomponentregisterimpl.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
