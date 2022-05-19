# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_objects OBJECT
    SOURCES
    asciiserializer.cpp
    deserializer.cpp
    floatingpointtype.cpp
    hexdump.cpp
    identifiable.cpp
    nboserializer.cpp
    nbostream.cpp
    object2slime.cpp
    objectdumper.cpp
    objectoperation.cpp
    objectpredicate.cpp
    objectvisitor.cpp
    serializer.cpp
    visit.cpp
    DEPENDS
)
