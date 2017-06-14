# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_searchlib_uca
    SOURCES
    ucaconverter.cpp
    ucafunctionnode.cpp
    INSTALL lib64
    DEPENDS
    searchlib
    icui18n
    icuuc
)
