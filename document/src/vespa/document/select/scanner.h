// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#if !defined(yyFlexLexerOnce)
#  include <FlexLexer.h>
#endif

#include "parser.hxx"
#include "location.hh"
#include <iosfwd>

namespace document::select {

class DocSelScanner final : yyFlexLexer {
public:
    explicit DocSelScanner(std::istream* in) : yyFlexLexer(in) {}
    ~DocSelScanner() override = default;
    int yylex(DocSelParser::semantic_type* yylval, DocSelParser::location_type* yyloc);
};

}
