# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_updates OBJECT
    SOURCES
    addfieldpathupdate.cpp
    addvalueupdate.cpp
    arithmeticvalueupdate.cpp
    assignfieldpathupdate.cpp
    assignvalueupdate.cpp
    clearvalueupdate.cpp
    documentupdate.cpp
    fieldpathupdate.cpp
    fieldupdate.cpp
    mapvalueupdate.cpp
    removefieldpathupdate.cpp
    removevalueupdate.cpp
    tensor_add_update.cpp
    tensor_modify_update.cpp
    tensor_partial_update.cpp
    tensor_remove_update.cpp
    valueupdate.cpp
    DEPENDS
)
