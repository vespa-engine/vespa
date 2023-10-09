# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_feedoperation STATIC
    SOURCES
    compact_lid_space_operation.cpp
    createbucketoperation.cpp
    deletebucketoperation.cpp
    documentoperation.cpp
    joinbucketsoperation.cpp
    lidvectorcontext.cpp
    moveoperation.cpp
    newconfigoperation.cpp
    noopoperation.cpp
    pruneremoveddocumentsoperation.cpp
    putoperation.cpp
    removedocumentsoperation.cpp
    removeoperation.cpp
    splitbucketoperation.cpp
    updateoperation.cpp
    DEPENDS
)
