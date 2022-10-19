# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    tensor_data_type.cpp
    weightedsetdatatype.cpp
    referencedatatype.cpp
    DEPENDS
)
