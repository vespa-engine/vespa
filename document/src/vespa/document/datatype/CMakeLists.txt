# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_datatypes OBJECT
    SOURCES
    annotationreferencedatatype.cpp
    annotationtype.cpp
    arraydatatype.cpp
    collectiondatatype.cpp
    datatype.cpp
    documenttype.cpp
    mapdatatype.cpp
    numericdatatype.cpp
    positiondatatype.cpp
    primitivedatatype.cpp
    structdatatype.cpp
    structureddatatype.cpp
    urldatatype.cpp
    weightedsetdatatype.cpp
    referencedatatype.cpp
    DEPENDS
    AFTER
    document_documentconfig
)
