# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(BISON REQUIRED 3.0)
find_package(FLEX REQUIRED 2.5)

BISON_TARGET(DocSelParser grammar/parser.yy
             ${CMAKE_CURRENT_BINARY_DIR}/parser.cxx
             DEFINES_FILE ${CMAKE_CURRENT_BINARY_DIR}/parser.hxx)
FLEX_TARGET(DocSelLexer grammar/lexer.ll
            ${CMAKE_CURRENT_BINARY_DIR}/lexer.cxx
            DEFINES_FILE ${CMAKE_CURRENT_BINARY_DIR}/lexer.hxx)

ADD_FLEX_BISON_DEPENDENCY(DocSelLexer DocSelParser)
include_directories(${CMAKE_CURRENT_BINARY_DIR})

vespa_add_source_target(bisongen_document_select DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/parser.cxx ${CMAKE_CURRENT_BINARY_DIR}/parser.hxx)
vespa_add_source_target(flexgen_document_select DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/lexer.cxx ${CMAKE_CURRENT_BINARY_DIR}/lexer.hxx)

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
    parser_limits.cpp
    ${BISON_DocSelParser_OUTPUTS}
    ${FLEX_DocSelLexer_OUTPUTS}
)

#TODO Remove once we have a recently new flex compiler. At least 2.5.38/39 or 2.6
set_source_files_properties(${FLEX_DocSelLexer_OUTPUTS} PROPERTIES COMPILE_FLAGS -Wno-register)
if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
  set_source_files_properties(${BISON_DocSelParser_OUTPUTS} PROPERTIES COMPILE_FLAGS -Wno-noexcept)
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 15.0)
  set_source_files_properties(${BISON_DocSelParser_OUTPUTS} PROPERTIES COMPILE_OPTIONS "-Wno-unused-but-set-variable")
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
  set_source_files_properties(${BISON_DocSelParser_OUTPUTS} PROPERTIES COMPILE_OPTIONS "-Wno-deprecated-copy-with-user-provided-copy")
endif()
