# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_select OBJECT
    SOURCES
    bodyfielddetector.cpp
    branch.cpp
    cloningvisitor.cpp
    compare.cpp
    constant.cpp
    context.cpp
    doctype.cpp
    gid_filter.cpp
    invalidconstant.cpp
    operator.cpp
    orderingselector.cpp
    orderingspecification.cpp
    parser.cpp
    result.cpp
    resultset.cpp
    resultlist.cpp
    simpleparser.cpp
    traversingvisitor.cpp
    value.cpp
    valuenode.cpp
    valuenodes.cpp
    AFTER
    document_documentconfig
)
