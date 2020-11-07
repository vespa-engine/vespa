# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_objects OBJECT
    SOURCES
    identifiable.cpp
    namedobject.cpp
    objectvisitor.cpp
    objectdumper.cpp
    object2slime.cpp
    visit.cpp
    objectpredicate.cpp
    objectoperation.cpp
    nboserializer.cpp
    serializer.cpp
    deserializer.cpp
    asciiserializer.cpp
    floatingpointtype.cpp
    DEPENDS
)
