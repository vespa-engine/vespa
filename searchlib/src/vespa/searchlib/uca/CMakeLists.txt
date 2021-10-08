# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(ICU 60.0 REQUIRED COMPONENTS uc i18n)
vespa_add_library(searchlib_searchlib_uca
    SOURCES
    ucaconverter.cpp
    ucafunctionnode.cpp
    INSTALL lib64
    DEPENDS
    searchlib
    ICU::i18n
    ICU::uc
)
