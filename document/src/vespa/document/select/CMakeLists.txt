# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(BISON REQUIRED)
find_package(FLEX REQUIRED)

BISON_TARGET(DocSelParser grammar/parser.yy ${CMAKE_CURRENT_BINARY_DIR}/parser.cxx)
FLEX_TARGET(DocSelLexer grammar/lexer.ll ${CMAKE_CURRENT_BINARY_DIR}/lexer.cxx)

ADD_FLEX_BISON_DEPENDENCY(DocSelLexer DocSelParser)
include_directories(${CMAKE_CURRENT_BINARY_DIR})

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
    result.cpp
    resultset.cpp
    resultlist.cpp
    simpleparser.cpp
    traversingvisitor.cpp
    value.cpp
    valuenode.cpp
    valuenodes.cpp
    parser.cpp
    parse_utils.cpp
    parsing_failed_exception.cpp
    ${BISON_DocSelParser_OUTPUTS}
    ${FLEX_DocSelLexer_OUTPUTS}
    AFTER
    document_documentconfig
)
