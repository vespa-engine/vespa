# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_select OBJECT
    SOURCES
    branch.cpp
    compare.cpp
    constant.cpp
    doctype.cpp
    operator.cpp
    parser.cpp
    result.cpp
    value.cpp
    valuenode.cpp
    orderingselector.cpp
    orderingspecification.cpp
    resultlist.cpp
    invalidconstant.cpp
    bodyfielddetector.cpp
    simpleparser.cpp
    resultset.cpp
    traversingvisitor.cpp
    cloningvisitor.cpp
    context.cpp
    gid_filter.cpp
    AFTER
    document_documentconfig
)
