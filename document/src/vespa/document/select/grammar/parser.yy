 /* Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. */

 /* Skeleton implementation included as part of the generated source. Note: _not_ covered by the GPL. */
%skeleton "lalr1.cc"

%require "3.0"

 /* Uncomment to enable debugging of lexer invocations */
 /*%debug*/

%locations

%define parse.error verbose
%define parse.assert

%define api.token.prefix {T_}
%define api.namespace {document::select}
%define parser_class_name {DocSelParser}

 /*
  * Due to current Bison variant support not being move-enabled (and our AST ptrs being move-only),
  * we have to use good old POD unions for our rule results. Note that we have to use %destructor
  * for all ptrs to ensure cleanup.
  */
%union {
    int64_t i64_val;
    double double_val;
    const char* const_str_val;
    vespalib::string* string_val;
    Constant* constant_node;
    ValueNode* value_node;
    FieldExprNode* field_expr_node;
    Node* abstract_node;
}

%token END 0 "end of input"
%token NULL
%token TRUE
%token FALSE
%token AND
%token OR
%token NOT

 /* Specify aliases for several tokens for ease of use and better error reporting */
%token GLOB  "="
%token REGEX "=~"
%token EQ    "=="
%token NE    "!="
%token GE    ">="
%token LE    "<="
%token GT    ">"
%token LT    "<"
%token NOW_FUNC

 /*
  * Tokens that we only mention by alias in the grammar rules, but which we define
  * explicitly to improve error reporting
  */
%token DOLLAR   "$"
%token DOT      "."
%token LPAREN   "("
%token RPAREN   ")"
%token COMMA    ","
%token PLUS     "+"
%token MINUS    "-"
%token MULTIPLY "*"
%token DIVIDE   "/"
%token MODULO   "%"

%token <string_val>  IDENTIFIER
%token <string_val>  STRING
%token <string_val>  FP_MAP_LOOKUP FP_ARRAY_LOOKUP
%token <double_val>  FLOAT
%token <i64_val>     INTEGER
%token <string_val>  ID USER GROUP SCHEME NAMESPACE SPECIFIC BUCKET GID TYPE

%type <string_val> ident mangled_ident
%type <value_node> bool_
 /* TODO 'leaf' is a bad name for something that isn't a leaf... */
%type <abstract_node> expression comparison logical_expr leaf doc_type
%type <string_val> id_arg
%type <value_node> number null_ value string arith_expr id_spec variable
%type <field_expr_node> field_spec

%destructor { delete $$; } IDENTIFIER STRING FP_MAP_LOOKUP FP_ARRAY_LOOKUP
%destructor { delete $$; } ID USER GROUP SCHEME NAMESPACE SPECIFIC BUCKET GID TYPE
%destructor { delete $$; } null_ bool_ number string doc_type ident id_arg id_spec
%destructor { delete $$; } variable mangled_ident field_spec value arith_expr
%destructor { delete $$; } comparison leaf logical_expr expression

%start entry

%parse-param {DocSelScanner& scanner}
%parse-param {const BucketIdFactory& bucket_id_factory}
%parse-param {const DocumentTypeRepo& doc_type_repo}
%parse-param {std::unique_ptr<Node>& recv_expr}

 /* Generated parser header file verbatim */
%code requires {

#include "location.hh"
#include <vespa/document/select/constant.h>
#include <vespa/document/select/branch.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/valuenodes.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document {
class BucketIdFactory;
class DocumentTypeRepo;
}

namespace document::select {
class DocSelScanner;
class Node;
class Constant;
class ValueNode;
}

}

%code {

// Bison has some chunky destructors that trigger inlining warnings. Disable warning
// for this translation unit, since we can't really do much about the code it generates.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline"

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/scanner.h>
#include <vespa/document/select/constant.h>
#include <vespa/document/select/branch.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/doctype.h>
#include <vespa/document/select/valuenodes.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <string>
#include <iostream>
#include <sstream>
#include <memory>

using string = vespalib::string;

// Wrap grabbing pointers from sub-rules in a way that nulls out the
// stored attribute from the Bison stack. Otherwise, exception cleanup
// in the parser code will attempt to double-delete the pointee.
// Yes, it's not beautiful, but that's life when you're dealing with raw pointers.
template <typename T>
std::unique_ptr<T> steal(T*& ptr) noexcept {
    std::unique_ptr<T> owned(ptr);
    ptr = nullptr;
    return owned;
}

// yylex tokenization must defer to scanner instance given to parser
#undef yylex
#define yylex scanner.yylex

}

%code provides {

// This cute little indirection is to get around the syntax_error constructor
// being defined as inline and therefore not being available outside the
// auto-generated parser source file.
[[noreturn]] void throw_parser_syntax_error(const document::select::DocSelParser::location_type& loc,
                                            const std::string& msg);

}


%left OR
%left AND
%left EQ NE LT GT LE GE GLOB REGEX
%left PLUS MINUS
%left MULTIPLY DIVIDE
%left MODULO /* Matches legacy parser recursive descent precedence */
%precedence NEG
%right UNOT
%left NON_DOT
%precedence DOT /* Used to give higher precedence to id.foo vs id expressions. Re: "dangling else" problem */

%%

null_
    : NULL { $$ = new NullValueNode(); }
    ;

bool_
    : TRUE   { $$ = new BoolValueNode(true); }
    | FALSE  { $$ = new BoolValueNode(false); }
    ;

number
    : INTEGER { $$ = new IntegerValueNode($1, false); }
    | FLOAT   { $$ = new FloatValueNode($1); }
    ;

string
    : STRING { {
          try {
              $$ = new StringValueNode(StringUtil::unescape(*steal<string>($1)));
          } catch (const vespalib::IllegalArgumentException& exc) {
              throw syntax_error(@$, exc.getMessage());
          }
     } }
    ;

doc_type
    : ident {
          if (doc_type_repo.getDocumentType(*$1) == nullptr) {
              throw syntax_error(@$, vespalib::make_string("Document type '%s' not found", $1->c_str()));
          }
          $$ = new DocType(*steal<string>($1));
      }
    ;

 /* We allow most otherwise reserved tokens to be used as identifiers. */
ident
    : IDENTIFIER { $$ = $1; }
    | USER       { $$ = $1; }
    | GROUP      { $$ = $1; }
    | SCHEME     { $$ = $1; }
    | TYPE       { $$ = $1; }
    | NAMESPACE  { $$ = $1; }
    | SPECIFIC   { $$ = $1; }
    | BUCKET     { $$ = $1; }
    | GID        { $$ = $1; }
    ;

id_arg
    : USER      { $$ = $1; }
    | GROUP     { $$ = $1; }
    | SCHEME    { $$ = $1; }
    | NAMESPACE { $$ = $1; }
    | SPECIFIC  { $$ = $1; }
    | BUCKET    { $$ = $1; }
    | GID       { $$ = $1; }
    | TYPE      { $$ = $1; }
    ;

id_spec
    : ID %prec NON_DOT {
          (void)steal<string>($1); // Explicitly discard.
          $$ = new IdValueNode(bucket_id_factory, "id", ""); // Prefer shifting instead of reducing.
      }
    | ID "." id_arg {
          (void)steal<string>($1); // Explicitly discard.
          $$ = new IdValueNode(bucket_id_factory, "id", *steal<string>($3));
      }
    | ID "." IDENTIFIER "(" ")" {
          (void)steal<string>($1);  // Explicitly discard.
          $$ = new FunctionValueNode(*steal<string>($3), std::make_unique<IdValueNode>(bucket_id_factory, "id", ""));
      }
    ;

variable
    : "$" ident { $$ = new VariableValueNode(*steal<string>($2)); }
    ;

 /* FIXME this is a horrible leftover of post-parsed fieldpath processing */
 /* At least we verify structural integrity at initial parse-time now...   */
 /* Post-parsing should be replaced with an actual parse-time built AST!   */
 /* This rule is only used after matching an initial valid identifier, so  */
 /* we add some special casing of lexer keywords that today are allowed as */
 /* regular field names (but not as document type names). Not pretty, but  */
 /* it avoids parser ambiguities.                                          */
mangled_ident
    : ident                         { $$ = $1; }
    | mangled_ident FP_MAP_LOOKUP   { $1->append(*steal<string>($2)); $$ = $1; }
    | mangled_ident FP_ARRAY_LOOKUP { $1->append(*steal<string>($2)); $$ = $1; }
    | ID                            { $$ = $1; }
    ;

field_spec
    : ident "." mangled_ident {
          if (doc_type_repo.getDocumentType(*$1) == nullptr) {
              throw syntax_error(@$, vespalib::make_string("Document type '%s' not found", $1->c_str()));
          }
          $$ = new FieldExprNode(std::make_unique<FieldExprNode>(*steal<string>($1)), *steal<string>($3));
      }
    | field_spec "." mangled_ident { $$ = new FieldExprNode(steal<FieldExprNode>($1), *steal<string>($3)); }
    ;

value
    : null_      { $$ = $1; }
    | bool_      { $$ = $1; }
    | string     { $$ = $1; }
    | id_spec    { $$ = $1; }
    | variable   { $$ = $1; }
    | NOW_FUNC   { $$ = new CurrentTimeValueNode(); }
    ;

arith_expr
    : value                         { $$ = $1; }
    | number                        { $$ = $1; }
    /* JavaCC and legacy parsers don't support unary plus/minus for _expressions_, just for numbers. So we have to fudge this a bit. */
    | "-" number %prec NEG  {
          if (dynamic_cast<IntegerValueNode*>($2) != nullptr) {
              $$ = new IntegerValueNode(- static_cast<IntegerValueNode&>(*steal<ValueNode>($2)).getValue(), false);
          } else {
              $$ = new FloatValueNode(- dynamic_cast<FloatValueNode&>(*steal<ValueNode>($2)).getValue());
          }
      }
    | "+" number %prec NEG      { $$ = $2; }
    | field_spec                { $$ = steal<FieldExprNode>($1)->convert_to_field_value().release(); }
    | field_spec "(" ")"        { $$ = steal<FieldExprNode>($1)->convert_to_function_call().release(); }
    | arith_expr "+" arith_expr { $$ = new ArithmeticValueNode(steal<ValueNode>($1), "+", steal<ValueNode>($3)); }
    | arith_expr "-" arith_expr { $$ = new ArithmeticValueNode(steal<ValueNode>($1), "-", steal<ValueNode>($3)); }
    | arith_expr "*" arith_expr { $$ = new ArithmeticValueNode(steal<ValueNode>($1), "*", steal<ValueNode>($3)); }
    | arith_expr "/" arith_expr { $$ = new ArithmeticValueNode(steal<ValueNode>($1), "/", steal<ValueNode>($3)); }
    | arith_expr "%" arith_expr { $$ = new ArithmeticValueNode(steal<ValueNode>($1), "%", steal<ValueNode>($3)); }
    | "(" arith_expr ")"        { $$ = $2; $$->setParentheses(); }
    | arith_expr "." IDENTIFIER "(" ")" { $$ = new FunctionValueNode(*steal<string>($3), steal<ValueNode>($1)); } /* FIXME shift/reduce conflict */
    ;

comparison
    : arith_expr EQ arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::EQ, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr NE arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::NE, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr GE arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::GEQ, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr LE arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::LEQ, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr GT arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::GT, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr LT arith_expr    { $$ = new Compare(steal<ValueNode>($1), FunctionOperator::LT, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr GLOB arith_expr  { $$ = new Compare(steal<ValueNode>($1), GlobOperator::GLOB, steal<ValueNode>($3), bucket_id_factory); }
    | arith_expr REGEX arith_expr { $$ = new Compare(steal<ValueNode>($1), RegexOperator::REGEX, steal<ValueNode>($3), bucket_id_factory); }
    ;

leaf
    : comparison { $$ = $1; }
    | doc_type   { $$ = $1; }
    | arith_expr { /* Actually bool_ or field_spec, see comment below..! */
          // Grammar-wise, we _do not_ accept arbitrary arith_exprs at this level. But the
          // selection grammar as it stands is otherwise ambiguous with LR(1) parsing.
          // More specifically, if we used field_spec instead of arith_expr here, the parser
          // state machine cannot decide what to do if it has processed the sequence '(' field_spec
          // and sees the next token of ')'. Since both logical_expr and arith_expr allows for
          // parenthesis expression recursion, the reduce step may produce either of these and
          // is therefore technically undefined. By using arith_expr instead for this rule, all
          // '(' field_spec ')' sequences result in an arith_expr rule match and the reduce/reduce
          // conflict goes away. We can then do a sneaky "run-time" type check to ensure we only
          // get the expected node from the rule.
          // It's not pretty, but it avoids an undefined grammar (which is much less pretty!).
          // The same goes for boolean constants, which may be used both as higher-level (non-value)
          // nodes or as value nodes to compare against.
          auto node = steal<ValueNode>($1);
          if (auto* as_bool = dynamic_cast<BoolValueNode*>(node.get())) {
              $$ = new Constant(as_bool->bool_value()); // Replace single bool node subtree with constant.
          } else {
              if (dynamic_cast<FieldValueNode*>(node.get()) == nullptr) {
                  throw syntax_error(@$, "expected field spec, doctype, bool or comparison");
              }
              // Implicit rewrite to non-null comparison node
              $$ = new Compare(std::move(node),
                               FunctionOperator::NE,
                               std::make_unique<NullValueNode>(),
                               bucket_id_factory);
          }
      }
    ;

logical_expr
    : leaf                          { $$ = $1; }
    | logical_expr AND logical_expr { $$ = new And(steal<Node>($1), steal<Node>($3)); }
    | logical_expr OR logical_expr  { $$ = new Or(steal<Node>($1), steal<Node>($3)); }
    | NOT logical_expr %prec UNOT   { $$ = new Not(steal<Node>($2)); }
    | "(" logical_expr ")"          { $$ = $2; $$->setParentheses(); }
    ;

expression
    : logical_expr { $$ = $1; }
    ;

entry
    : expression END { recv_expr = steal<Node>($1); }
    | END            { recv_expr = std::make_unique<Constant>(true); }
    ;

%%

void document::select::DocSelParser::error(const location_type& l, const std::string& what) {
    throw syntax_error(l, what);
}

void throw_parser_syntax_error(const document::select::DocSelParser::location_type& loc, const std::string& msg) {
    throw document::select::DocSelParser::syntax_error(loc, msg);
}

#pragma GCC diagnostic pop
