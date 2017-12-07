 /* Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. */

 /* We use the .*xx-suffix to denote a build-time generated file */
%option outfile="lexer.cxx"
%option header-file="lexer.hxx"

%option c++
 /* Uncomment to enable debug tracing of parsing */
 /* %option debug */
%option 8bit warn nodefault
%option noyywrap nounput
%option yyclass="document::select::DocSelScanner"

 /* Used to track source locations, see https://github.com/bingmann/flex-bison-cpp-example/blob/master/src/scanner.ll */
%{
#define YY_USER_ACTION yyloc->columns(yyleng);
%}

%{

#include "parser.hxx"
#include <vespa/document/select/scanner.h>
#include <vespa/document/select/parse_utils.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/stllike/string.h>
#include <string>
#include <cstdlib>

#undef YY_DECL
#define YY_DECL int document::select::DocSelScanner::yylex( \
        document::select::DocSelParser::semantic_type* yylval, \
        document::select::DocSelParser::location_type* yyloc)

using token = document::select::DocSelParser::token;
using string = vespalib::string;

// Inspired by https://coldfix.eu/2015/05/16/bison-c++11/

#define YIELD_TOKEN(name, field_name, value) \
    yylval->field_name = value; \
    return token::T_##name;

#define INT_TOKEN(name, value) YIELD_TOKEN(name, i64_val, value)
#define STRING_TOKEN(name) YIELD_TOKEN(name, string_val, new string(yytext, yyleng))
#define CONST_STR_TOKEN(name, value) YIELD_TOKEN(name, const_str_val, value)
#define TAGGED_TOKEN INT_TOKEN

#define NAMED_TOKEN(name) return token::T_##name;

%}

 /* Lexer fragments, used as part of token patterns */

SIGN [+-]
DECIMAL [0-9]+
HEXDIGIT [0-9a-fA-F]
HEX 0[xX]{HEXDIGIT}{1,16}
OCTAL 0[0-7]*
EXPONENT [eE][+-]?[0-9]+
IDCHARS [a-zA-Z_][a-zA-Z_0-9_]*
WS [ \f\r\t]

 /* It is weird that you can't do \' inside "" and vice versa, but that's the StringUtil::unescape logic today... */
DQ_STRING \"(\\([\\tnfr"]|x{HEXDIGIT}{2})|[^"\\])*\"
SQ_STRING \'(\\([\\tnfr']|x{HEXDIGIT}{2})|[^'\\])*\'

%%

 /* Code to take place at the beginning of yylex() */
%{
  // TODO move to YY_USER_ACTION instead?
  yyloc->step();
%}

 /* TODO support length suffixes? supported in JavaCC grammar, but not in legacy Spirit grammar... */
{HEX} {
    // TODO replace with std::from_string() once compiler support is there
    if (!util::parse_hex_i64(yytext + 2, yyleng - 2, yylval->i64_val)) { // Skip 0[xX] prefix
        throw_parser_syntax_error(*yyloc, "Not a valid 64-bit hex integer: " + std::string(yytext, yyleng));
    }
    return token::T_INTEGER;
}

 /* Sign is handled explicitly in the parser to avoid lexing ambiguities for expressions such as "1 -2" */
{DECIMAL} {
    if (!util::parse_i64(yytext, yyleng, yylval->i64_val)) {
        throw_parser_syntax_error(*yyloc, "Not a valid signed 64-bit integer: " + std::string(yytext, yyleng));
    }
    return token::T_INTEGER;
}

 /*
  * We use a strict definition of floats when lexing, i.e. we require a dot
  * in order to remove ambiguities with the base 10 integer token.
  */
[0-9]+(\.[0-9]*){EXPONENT}?[fFdD]? {
    if (!util::parse_double(yytext, yyleng, yylval->double_val)) {
        throw_parser_syntax_error(*yyloc, "Not a valid floating point number: " + std::string(yytext, yyleng));
    }
    return token::T_FLOAT;
}

({DQ_STRING}|{SQ_STRING}) {
    // Always slice off start and end quote chars
    yylval->string_val = new string(yytext + 1, yyleng - 2);
    return token::T_STRING;
}

 /* FIXME this is a syntactic hack to "flatten" fieldpath map and array lookups into a single token
    rather than match these structurally in the parser itself. This is due to the way fieldpaths
    are handled in the legacy AST (i.e. as strings, not structures), and this must be changed first
    before we can fix this. */
 /* Field path expressions do not support any other escapes than double quote char */
 /* TODO {WS} does not include newline, do we need to support that here? */
\{{WS}*($?{IDCHARS}|{DECIMAL}|\"([^\\\"]|\\\")*\"){WS}*\} STRING_TOKEN(FP_MAP_LOOKUP)
\[{WS}*(${IDCHARS}|{DECIMAL}){WS}*\]                      STRING_TOKEN(FP_ARRAY_LOOKUP)

 /* Primary tokens are case insensitive */
(?i:"id")    NAMED_TOKEN(ID)
(?i:"null")  NAMED_TOKEN(NULL)
(?i:"true")  NAMED_TOKEN(TRUE)
(?i:"false") NAMED_TOKEN(FALSE)
(?i:"and")   NAMED_TOKEN(AND)
(?i:"or")    NAMED_TOKEN(OR)
(?i:"not")   NAMED_TOKEN(NOT)

 /* We expose the verbatim input as the token value, as these may also be used for identifiers... */
(?i:"user")      STRING_TOKEN(USER)
(?i:"group")     STRING_TOKEN(GROUP)
(?i:"scheme")    STRING_TOKEN(SCHEME)
(?i:"namespace") STRING_TOKEN(NAMESPACE)
(?i:"specific")  STRING_TOKEN(SPECIFIC)
(?i:"bucket")    STRING_TOKEN(BUCKET)
(?i:"gid")       STRING_TOKEN(GID)
(?i:"type")      STRING_TOKEN(TYPE)
(?i:"order")     STRING_TOKEN(ORDER)

"now\(\)" NAMED_TOKEN(NOW_FUNC) /* This _is_ case-sensitive in the legacy parser */

 /* Binary operators */
 /* TODO INT_TOKEN with code directly from selection operator node? Or direct operator object ptr? */
"="  NAMED_TOKEN(GLOB)
"=~" NAMED_TOKEN(REGEX)
"==" NAMED_TOKEN(EQ)
"!=" NAMED_TOKEN(NE)
">=" NAMED_TOKEN(GE)
"<=" NAMED_TOKEN(LE)
">"  NAMED_TOKEN(GT)
"<"  NAMED_TOKEN(LT)

"$"  NAMED_TOKEN(DOLLAR)
"."  NAMED_TOKEN(DOT)
"("  NAMED_TOKEN(LPAREN)
")"  NAMED_TOKEN(RPAREN)
","  NAMED_TOKEN(COMMA)
"+"  NAMED_TOKEN(PLUS)
"-"  NAMED_TOKEN(MINUS)
"*"  NAMED_TOKEN(MULTIPLY)
"/"  NAMED_TOKEN(DIVIDE)
"%"  NAMED_TOKEN(MODULO)

{IDCHARS} STRING_TOKEN(IDENTIFIER)

\n {
    yyloc->lines(yyleng);
    yyloc->step();
    return yytext[0];
}

{WS} {
    yyloc->step();
}

 /*
  * Everything that hasn't already matched is an error. Throw exception immediately with the exact
  * char to avoid getting auto-generated error messages with "unexpected $undefined" due to the
  * resulting token not matching any existing, explicitly named tokens.
  */
. { throw_parser_syntax_error(*yyloc, "Unexpected character: '" + StringUtil::escape(vespalib::string(yytext, 1)) + "'"); }

%%

