// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parser.h"
#include "branch.h"
#include "compare.h"
#include "constant.h"
#include "operator.h"
#include "doctype.h"
#include "valuenode.h"
#include "simpleparser.h"

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <boost/spirit/include/classic_chset.hpp>
#include <boost/spirit/include/classic_core.hpp>
#include <boost/spirit/include/classic_escape_char.hpp>
#include <boost/spirit/include/classic_grammar_def.hpp>
#include <boost/spirit/include/classic_parse_tree.hpp>
#include <boost/spirit/include/classic_tree_to_xml.hpp>
#include <iostream>
#include <map>
#include <sstream>

using boost::spirit::classic::tree_node;
using document::DocumentTypeRepo;
using std::unique_ptr;
using std::cerr;
using std::endl;
using std::istringstream;
using std::ostringstream;
using vespalib::IllegalStateException;

/*
 * This cannot be part of a plugin.  boost contains constructs causing
 * compiler to generate calls to atexit().
 */

#define parse_assert(a)

namespace document {
namespace select {

VESPA_IMPLEMENT_EXCEPTION(ParsingFailedException, vespalib::Exception);

Parser::Parser(const DocumentTypeRepo& repo,
               const BucketIdFactory& bucketIdFactory)
    : _repo(repo),
      _bucketIdFactory(bucketIdFactory)
{
}

namespace {

/**
 * Defines the grammar for the document selection text format.
 */
struct DocSelectionGrammar
    : public boost::spirit::classic::grammar<DocSelectionGrammar>
{
    /** Node identifiers (value 0 should not be used) */
    enum ids { id_nil=1, id_bool, id_number, id_string,
               id_doctype, id_fieldname, id_function, id_idarg, id_searchcolumnarg,
               id_operator, id_idspec, id_searchcolumnspec, id_fieldspec, id_value,
               id_valuefuncadd, id_valuefuncmul, id_valuefuncmod,
               id_valuegroup, id_arithmvalue,
               id_comparison, id_leaf, id_not, id_and,
               id_or, id_group, id_order, id_expression, id_variable };

    const DocumentTypeRepo &_repo;
    const BucketIdFactory& _bucketIdFactory;

    DocSelectionGrammar(const DocumentTypeRepo& repo,
                        const BucketIdFactory& bucketIdFactory)
        : _repo(repo),
          _bucketIdFactory(bucketIdFactory) {}

    const BucketIdFactory& getBucketIdFactory() const
    { return _bucketIdFactory; }

    /** Grammar base types. To be able to retrieve different grammars. */
    template <typename Scanner>
    struct gram_base {
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_nil> > rule_nil;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_bool> > rule_bool;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_number> > rule_number;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_string> > rule_string;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_doctype> > rule_doctype;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_fieldname> > rule_fieldname;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_function> > rule_function;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_idarg> > rule_idarg;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_searchcolumnarg> > rule_searchcolumnarg;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_operator> > rule_operator;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_idspec> > rule_idspec;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_searchcolumnspec> > rule_searchcolumnspec;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_fieldspec> > rule_fieldspec;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_value> > rule_value;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_valuefuncadd> > rule_valuefuncadd;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_valuefuncmul> > rule_valuefuncmul;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_valuefuncmod> > rule_valuefuncmod;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_valuegroup> > rule_valuegroup;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_arithmvalue> > rule_arithmvalue;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_comparison> > rule_comparison;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_leaf> > rule_leaf;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_not> > rule_not;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_and> > rule_and;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_or> > rule_or;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_group> > rule_group;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_order> > rule_order;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_expression> > rule_expression;
        typedef typename boost::spirit::classic::rule<Scanner,
                                             boost::spirit::classic::parser_tag<id_variable> > rule_variable;
        typedef boost::spirit::classic::grammar_def<rule_expression,
                                           rule_leaf,
                                           rule_arithmvalue> type;
    };

    template <typename Scanner>
    struct definition : gram_base<Scanner>::type
    {
        typename gram_base<Scanner>::rule_nil _nil;
        typename gram_base<Scanner>::rule_bool _bool;
        typename gram_base<Scanner>::rule_number _number;
        typename gram_base<Scanner>::rule_string _string;
        typename gram_base<Scanner>::rule_doctype _doctype;
        typename gram_base<Scanner>::rule_fieldname _fieldname;
        typename gram_base<Scanner>::rule_function _function;
        typename gram_base<Scanner>::rule_idarg _idarg;
        typename gram_base<Scanner>::rule_searchcolumnarg _searchcolumnarg;
        typename gram_base<Scanner>::rule_operator _operator;
        typename gram_base<Scanner>::rule_idspec _idspec;
        typename gram_base<Scanner>::rule_searchcolumnspec _searchcolumnspec;
        typename gram_base<Scanner>::rule_fieldspec _fieldspec;
        typename gram_base<Scanner>::rule_value _value;
        typename gram_base<Scanner>::rule_valuefuncadd _valuefuncadd;
        typename gram_base<Scanner>::rule_valuefuncmul _valuefuncmul;
        typename gram_base<Scanner>::rule_valuefuncmod _valuefuncmod;
        typename gram_base<Scanner>::rule_valuegroup _valuegroup;
        typename gram_base<Scanner>::rule_arithmvalue _arithmvalue;
        typename gram_base<Scanner>::rule_comparison _comparison;
        typename gram_base<Scanner>::rule_leaf _leaf;
        typename gram_base<Scanner>::rule_not _not;
        typename gram_base<Scanner>::rule_and _and;
        typename gram_base<Scanner>::rule_or _or;
        typename gram_base<Scanner>::rule_group _group;
        typename gram_base<Scanner>::rule_order _order;
        typename gram_base<Scanner>::rule_expression _expression;
        typename gram_base<Scanner>::rule_variable _variable;

        definition(const DocSelectionGrammar&)
            : _nil(),
              _bool(),
              _number(),
              _string(),
              _doctype(),
              _fieldname(),
              _function(),
              _idarg(),
              _operator(),
              _idspec(),
              _searchcolumnspec(),
              _fieldspec(),
              _value(),
              _valuefuncadd(),
              _valuefuncmul(),
              _valuefuncmod(),
              _valuegroup(),
              _arithmvalue(),
              _comparison(),
              _leaf(),
              _not(),
              _and(),
              _or(),
              _group(),
              _order(),
              _expression(),
              _variable()
        {
            using namespace boost::spirit::classic;

            boost::spirit::classic::uint_parser<uint64_t, 16, 1, -1> hexvalue;

            // Initialize primitives
            _nil = lexeme_d[ as_lower_d["null"] ];
            _bool = lexeme_d[ as_lower_d["true"] | as_lower_d["false"] ];
            _number = lexeme_d[ str_p("0x") >> hexvalue ] | lexeme_d[ real_p ];
            _string = ( lexeme_d[
                                ( no_node_d[ ch_p('"') ] >>
                                 token_node_d[ *( ~chset<>("\\\"\x00-\x1f\x7f-\xff") |
                                                       ( '\\' >> ( ch_p('\\') | 't' | 'n' | 'f' | 'r' | '"' |
                                                               (ch_p('x') >> xdigit_p >> xdigit_p) ) ) ) ] >>
                                 no_node_d[ ch_p('"') ] ) |
                                ( no_node_d[ ch_p('\'') ] >>
                                 token_node_d[ *( ~chset<>("\\'\x00-\x1f\x7f-\xff") |
                                                       ( '\\' >> ( ch_p('\\') | 't' | 'n' | 'f' | 'r' | '\'' |
                                                               (ch_p('x') >> xdigit_p >> xdigit_p) ) ) ) ] >>
                                 no_node_d[ ch_p('\'') ] )
                        ] );
            _doctype = lexeme_d[ token_node_d[ chset<>("_A-Za-z")
                                         >> *(chset<>("_A-Za-z0-9")) ]];
            _fieldname = lexeme_d[ token_node_d[chset<>("_A-Za-z")
                                 >> *(chset<>("_A-Za-z0-9{}[]$"))
                               ]];
            _function = lexeme_d[ token_node_d[ chset<>("A-Za-z")
                                                    >> *(chset<>("A-Za-z0-9")) ]
                                 >> no_node_d[ str_p("()") ] ];

            _order = as_lower_d["order"]
                     >> no_node_d[ ch_p('(') ]
                     >> _number
                     >> no_node_d[ ch_p(',') ]
                     >> _number
                     >> no_node_d[ ch_p(')') ];

            _idarg = (as_lower_d[ "scheme"]    | as_lower_d[ "namespace"] |
                      as_lower_d[ "specific" ] | as_lower_d[ "user" ] |
                      as_lower_d[ "group" ] | as_lower_d[ "bucket" ] | 
                      as_lower_d[ "gid" ] | as_lower_d["type"] | _order);

            _searchcolumnarg = lexeme_d[ token_node_d[ *(chset<>("_A-Za-z0-9")) ]];
            _operator = (str_p(">=") | ">" | "==" | "=~" | "="
                         | "<=" | "<" | "!=");
            // Derived
            _idspec = as_lower_d["id"]
                      >> !(no_node_d[ ch_p('.') ] >> _idarg);
            _searchcolumnspec = as_lower_d["searchcolumn"]
                                >> !(no_node_d[ ch_p('.') ] >> _searchcolumnarg);
            _fieldspec = _doctype
                         >> +( no_node_d[ ch_p('.') ] >> (_function | _fieldname));
            _variable = lexeme_d[ token_node_d[chset<>("$")
                                 >> *(chset<>("A-Za-z0-9"))
                               ]];
            _value = (_valuegroup | _function | _nil | _number | _string
                      | _idspec | _searchcolumnspec | _fieldspec | _variable)
                    >> *(no_node_d[ ch_p('.') ] >> _function);
            _valuefuncmod = (_valuegroup | _value)
                            >> +( ch_p('%')
                                 >> (_valuegroup | _value) );
            _valuefuncmul = (_valuefuncmod | _valuegroup | _value)
                            >> +( (ch_p('*') | ch_p('/'))
                                 >> (_valuefuncmod | _valuegroup | _value));
            _valuefuncadd
                = (_valuefuncmul | _valuefuncmod | _valuegroup | _value)
                >> +((ch_p('+') | ch_p('-'))
                     >> (_valuefuncmul | _valuefuncmod | _valuegroup |
                         _value));
            _valuegroup = no_node_d[ ch_p('(') ] >> _arithmvalue
                        >> no_node_d[ ch_p(')') ]
                        >> *(no_node_d[ ch_p('.') ] >> _function);
            _arithmvalue = (_valuefuncadd | _valuefuncmul | _valuefuncmod
                            | _valuegroup | _value);
            _comparison = _arithmvalue >> _operator >> _arithmvalue;
            _leaf = _bool | _comparison | _fieldspec | _doctype;

            _not = (as_lower_d["not"] >> _group)
                   | (lexeme_d[ as_lower_d["not"] >> no_node_d[ space_p ] ] >> _leaf);
            _and = (_not | _group | _leaf)
                   >> as_lower_d["and"] >> (_and | _not | _group | _leaf);
            _or  = (_and | _not | _group | _leaf)
                   >> as_lower_d["or"] >> (_or | _and | _not | _group | _leaf);
            _group =  no_node_d[ ch_p('(') ]
                      >> (_or | _and | _not | _group | _leaf)
                      >> no_node_d[ ch_p(')') ];

            _expression = !(_or | _and | _not | _group | _leaf)   >> end_p;

            this->start_parsers(_expression, _leaf, _arithmvalue);
        }
    };

};

template<typename T>
std::unique_ptr<Node>
parseTree(DocSelectionGrammar& grammar, tree_node<T>& root) {
    return parseNode(grammar, root);
}

template<typename T>
std::unique_ptr<Node>
parseNode(DocSelectionGrammar& grammar, tree_node<T>& node) {
    switch (node.value.id().to_long()) {
    case DocSelectionGrammar::id_or:
        return parseOr(grammar, node);
    case DocSelectionGrammar::id_and:
        return parseAnd(grammar, node);
    case DocSelectionGrammar::id_not:
        return parseNot(grammar, node);
    case DocSelectionGrammar::id_group:
    {
        std::unique_ptr<Node> n(parseNode(grammar, node.children[0]));
        n->setParentheses();
        return n;
    }
    case DocSelectionGrammar::id_leaf:
    case DocSelectionGrammar::id_value:
        parse_assert(node.children.size() == 1);
        return parseNode(grammar, node.children[0]);
    case DocSelectionGrammar::id_expression:
        if (node.children.size() == 1) {
            return parseNode(grammar, node.children[0]);
        }
        parse_assert(node.children.size() == 0);
        return std::unique_ptr<Node>(new Constant("true"));
    case DocSelectionGrammar::id_bool:
        return parseBool(grammar, node);
    case DocSelectionGrammar::id_comparison:
        return parseComparison(grammar, node);
    case DocSelectionGrammar::id_fieldspec:
        return parseFieldSpec(grammar, node);
    case DocSelectionGrammar::id_doctype:
        return parseDocType(grammar, node);
    }
    vespalib::asciistream ost;
    ost << "Received unhandled nodetype "
        << node.value.id().to_long() << " in parseNode()\n";
    throw IllegalStateException(ost.str(), VESPA_STRLOC);
}

template<typename T>
std::unique_ptr<Node>
parseOr(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_or);
    parse_assert(node.children.size() == 3);
    vespalib::string op(node.children[1].value.begin(),
                   node.children[1].value.end());
    return std::unique_ptr<Node>(new Or(
                                       parseNode(grammar, node.children[0]),
                                       parseNode(grammar, node.children[2]),
                                       op.c_str()));
}

template<typename T>
std::unique_ptr<Node>
parseAnd(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_and);
    parse_assert(node.children.size() == 3);
    vespalib::string op(node.children[1].value.begin(),
                   node.children[1].value.end());
    return std::unique_ptr<Node>(new And(
                                       parseNode(grammar, node.children[0]),
                                       parseNode(grammar, node.children[2]),
                                       op.c_str()));
}

template<typename T>
std::unique_ptr<Node>
parseNot(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_not);
    parse_assert(node.children.size() == 2);
    vespalib::string op(node.children[0].value.begin(),
                   node.children[0].value.end());
    return std::unique_ptr<Node>(new Not(
                                       parseNode(grammar, node.children[1]), op.c_str()));
}

template<typename T>
std::unique_ptr<Node>
parseBool(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_bool);
    parse_assert(node.children.size() == 1);
    parse_assert(node.children[0].value.id().to_long() == grammar.id_bool);
    parse_assert(node.children[0].children.size() == 0);
    vespalib::string s(node.children[0].value.begin(), node.children[0].value.end());
    return std::unique_ptr<Node>(new Constant(s));
}

template<typename T>
std::unique_ptr<Node>
parseComparison(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_comparison);
    parse_assert(node.children.size() == 3);
    parse_assert(node.children[1].children.size() == 1);
    vespalib::string op(node.children[1].children[0].value.begin(),
                   node.children[1].children[0].value.end());
    return std::unique_ptr<Node>(new Compare(
                                       parseArithmValue(grammar, node.children[0]),
                                       Operator::get(op),
                                       parseArithmValue(grammar, node.children[2]),
                                       grammar.getBucketIdFactory()));
}

template<typename T>
std::unique_ptr<Node>
parseFieldSpec(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_fieldspec);
    return std::unique_ptr<Node>(new Compare(
                                       parseFieldSpecValue(grammar, node),
                                       Operator::get("!="),
                                       std::unique_ptr<ValueNode>(new NullValueNode("null")),
                                       grammar.getBucketIdFactory()));
}

template<typename T>
std::unique_ptr<ValueNode>
parseVariable(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_variable);
    vespalib::string varName(node.children[0].value.begin(),
                        node.children[0].value.end());
    return std::unique_ptr<ValueNode>(new VariableValueNode(varName.substr(1)));
}

template<typename T>
std::unique_ptr<ValueNode>
parseGlobValueFunction(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_function);
    vespalib::string varName(node.children[0].value.begin(),
                             node.children[0].value.end());
    if (varName == "now") {
        return std::unique_ptr<ValueNode>(new CurrentTimeValueNode);
    }
    throw ParsingFailedException("Unexpected function name '" + varName
            + "' found.", VESPA_STRLOC);
}

template<typename T>
std::unique_ptr<Node>
parseDocType(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_doctype);
    parse_assert(node.children.size() == 1);
    parse_assert(node.children[0].value.id().to_long() == grammar.id_doctype);
    parse_assert(node.children[0].children.size() == 0);
    vespalib::string doctype(node.children[0].value.begin(),
                        node.children[0].value.end());
    // Verify existance of any version of document
    if (!grammar._repo.getDocumentType(doctype)) {
        throw ParsingFailedException("Document type " + doctype + " not found",
                                     VESPA_STRLOC);
    }
    return std::unique_ptr<Node>(new DocType(doctype));
}

template<typename T>
std::unique_ptr<ValueNode>
addFunctions(DocSelectionGrammar& grammar, tree_node<T>& node,
             std::unique_ptr<ValueNode> src, uint32_t index)
{
    (void) grammar;
    while (index < node.children.size()) {
        parse_assert(node.children[index].value.id().to_long()
               == grammar.id_function);
        vespalib::string func(node.children[index].children[0].value.begin(),
                         node.children[index].children[0].value.end());
        std::unique_ptr<ValueNode> fnode(new FunctionValueNode(func, std::move(src)));
        src = std::move(fnode);
        ++index;
    }
    return std::move(src);
}

template<typename T>
std::unique_ptr<ValueNode>
parseArithmValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    switch (node.value.id().to_long()) {
    case DocSelectionGrammar::id_arithmvalue:
        parse_assert(node.children.size() == 1);
        return parseArithmValue(grammar, node.children[0]);
    case DocSelectionGrammar::id_value:
        return parseValue(grammar, node);
    case DocSelectionGrammar::id_valuegroup:
        return parseValueGroup(grammar, node);
    case DocSelectionGrammar::id_valuefuncadd:
    case DocSelectionGrammar::id_valuefuncmul:
    case DocSelectionGrammar::id_valuefuncmod:
        return parseValueArithmetics(grammar, node);
    }
    vespalib::asciistream ost;
    ost << "Received unhandled nodetype "
        << node.value.id().to_long()
        << " in parseArithmValue()\n";
    throw IllegalStateException(ost.str(), VESPA_STRLOC);
}

template<typename T>
std::unique_ptr<ValueNode>
parseValueArithmetics(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.children.size() >= 3 && node.children.size() % 2 == 1);
    std::unique_ptr<ValueNode> lhs(parseArithmValue(grammar, node.children[0]));
    for (unsigned int i = 1; i < node.children.size(); i += 2) {
        vespalib::string op(node.children[i].value.begin(),
                            node.children[i].value.end());
        std::unique_ptr<ValueNode> rhs(parseArithmValue(grammar,
                                                      node.children[i + 1]));
        std::unique_ptr<ValueNode> res(
                new ArithmeticValueNode(std::move(lhs), op, std::move(rhs)));
        lhs = std::move(res);
    }
    return lhs;
}

template<typename T>
std::unique_ptr<ValueNode>
parseValueGroup(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_valuegroup);
    parse_assert(node.children.size() >= 1);
    std::unique_ptr<ValueNode> result(
            parseArithmValue(grammar, node.children[0]));
    result->setParentheses();
    return addFunctions(grammar, node, std::move(result), 1);
}

template<typename T>
std::unique_ptr<ValueNode>
parseValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_value);
    parse_assert(node.children.size() >= 1);
    std::unique_ptr<ValueNode> result;
    switch (node.children[0].value.id().to_long()) {
    case DocSelectionGrammar::id_nil:
        result = parseNilValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_idspec:
        result = parseIdSpecValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_searchcolumnspec:
        result = parseSearchColumnSpecValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_fieldspec:
        result = parseFieldSpecValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_number:
        result = parseNumberValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_string:
        result = parseStringValue(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_valuegroup:
        result = parseValueGroup(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_variable:
        result = parseVariable(grammar, node.children[0]);
        break;
    case DocSelectionGrammar::id_function:
        result = parseGlobValueFunction(grammar, node.children[0]);
        break;
    default:
        vespalib::asciistream ost;
        ost << "Received unhandled nodetype "
            << node.children[0].value.id().to_long()
            << " in parseValue(), from node of type "
            << node.value.id().to_long() << "\n";
        throw IllegalStateException(ost.str(), VESPA_STRLOC);
    }
    return addFunctions(grammar, node, std::move(result), 1);
}

template<typename T>
std::unique_ptr<ValueNode>
parseNilValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_nil);
    parse_assert(node.children.size() == 1);
    parse_assert(node.children[0].children.size() == 0);
    vespalib::string op(node.children[0].value.begin(),
                   node.children[0].value.end());
    return std::unique_ptr<ValueNode>(new NullValueNode(op));
}

template<typename T>
std::unique_ptr<ValueNode>
parseIdSpecValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_idspec);
    parse_assert(node.children.size() >= 1);
    parse_assert(node.children[0].children.size() == 0);
    vespalib::string id(node.children[0].value.begin(),
                   node.children[0].value.end());
    if (node.children.size() == 1) {
        return std::unique_ptr<ValueNode>(
                new IdValueNode(grammar.getBucketIdFactory(), id, ""));
    }

    vespalib::string type;

    int widthBits = -1;
    int divisionBits = -1;

    if (node.children[1].children[0].value.id().to_long() == grammar.id_order) {
        tree_node<T>& ordernode(node.children[1].children[0]);
        type = vespalib::string(ordernode.children[0].value.begin(),
                           ordernode.children[0].value.end());

        vespalib::string val = vespalib::string(
                ordernode.children[1].children[0].value.begin(),
                ordernode.children[1].children[0].value.end());
        widthBits = atoi(val.c_str());

        val = vespalib::string(ordernode.children[2].children[0].value.begin(),
                          ordernode.children[2].children[0].value.end());
        divisionBits = atoi(val.c_str());
    } else {
        type = vespalib::string(node.children[1].children[0].value.begin(),
                           node.children[1].children[0].value.end());
    }

    return std::unique_ptr<ValueNode>(
            new IdValueNode(grammar.getBucketIdFactory(), id, type,
                            widthBits, divisionBits));
}

template<typename T>
std::unique_ptr<ValueNode>
parseSearchColumnSpecValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_searchcolumnspec);
    parse_assert(node.children.size() == 2);
    parse_assert(node.children[0].children.size() == 0);
    parse_assert(node.children[1].value.id().to_long() == grammar.id_searchcolumnarg);

    vespalib::string id(node.children[0].value.begin(),
                   node.children[0].value.end());
    parse_assert(node.children.size() == 2);

    vespalib::string val = vespalib::string(node.children[1].children[0].value.begin(),
                                  node.children[1].children[0].value.end());
    return std::unique_ptr<ValueNode>(new SearchColumnValueNode(
                                            grammar.getBucketIdFactory(), id, atoi(val.c_str())));
}

template<typename T>
std::unique_ptr<ValueNode>
parseFieldSpecValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    parse_assert(node.value.id().to_long() == grammar.id_fieldspec);
    parse_assert(node.children.size() >= 2);
    parse_assert(node.children[0].value.id().to_long() == grammar.id_doctype);
    vespalib::string doctype(node.children[0].children[0].value.begin(),
                        node.children[0].children[0].value.end());
    // Verify that document type exist at any version
    if (!grammar._repo.getDocumentType(doctype)) {
        throw ParsingFailedException("Document type " + doctype + " not found",
                                     VESPA_STRLOC);
    }
    std::unique_ptr<ValueNode> value;
    uint32_t iterator = 2;

    parse_assert(node.children[1].value.id().to_long() == grammar.id_fieldname);
    vespalib::string field(node.children[1].children[0].value.begin(),
                           node.children[1].children[0].value.end());
    while (iterator < node.children.size()
           && node.children[iterator].value.id().to_long() == grammar.id_fieldname)
    {
        field += "." + vespalib::string(
                node.children[iterator].children[0].value.begin(),
                node.children[iterator].children[0].value.end());
        ++iterator;
    }
    value.reset(new FieldValueNode(doctype, field));

    for (; iterator<node.children.size(); ++iterator) {
        std::unique_ptr<ValueNode> child(std::move(value));
        vespalib::string function(node.children[iterator].children[0].value.begin(),
                             node.children[iterator].children[0].value.end());
        parse_assert(node.children[iterator].value.id().to_long() == grammar.id_function);
        value.reset(new FunctionValueNode(function, std::move(child)));
    }
    return value;
}

template<typename T>
std::unique_ptr<ValueNode>
parseNumberValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_number);
    vespalib::string sval;
    int base = 10;
    if (node.children.size() == 2) {
        base = 16;
        sval = vespalib::string(node.children[1].value.begin(),
                           node.children[1].value.end());
        parse_assert(node.children[0].value.id().to_long() == grammar.id_number);
        parse_assert(node.children[1].value.id().to_long() == grammar.id_number);
    } else {
        parse_assert(node.children.size() == 1);
        sval = vespalib::string(node.children[0].value.begin(),
                           node.children[0].value.end());
        parse_assert(node.children[0].value.id().to_long() == grammar.id_number);
    }
    if (sval.find('.') != vespalib::string::npos) {
        char* endptr;
        double val = strtod(sval.c_str(), &endptr);
        if (*endptr == '\0') {
            return std::unique_ptr<ValueNode>(new FloatValueNode(val));
        }
    } else {
        char* endptr;
        int64_t val;
        if (base == 16) {
            val = strtoull(sval.c_str(), &endptr, base);
        } else {
            val = strtoll(sval.c_str(), &endptr, base);
        }
        if (*endptr == '\0') {
            return std::unique_ptr<ValueNode>(new IntegerValueNode(val, false));
        }
    }
    vespalib::string error = "'" + sval + "' is not a valid number.";
    throw ParsingFailedException(error, VESPA_STRLOC);
}

template<typename T>
std::unique_ptr<ValueNode>
parseStringValue(DocSelectionGrammar& grammar, tree_node<T>& node) {
    (void) grammar;
    parse_assert(node.value.id().to_long() == grammar.id_string);
    if (node.children.size() == 0) {
        return std::unique_ptr<ValueNode>(new StringValueNode(""));
    }
    parse_assert(node.children.size() == 1);
    parse_assert(node.children[0].value.id().to_long() == grammar.id_string);
    vespalib::string val(node.children[0].value.begin(),
                    node.children[0].value.end());
    return std::unique_ptr<ValueNode>(new StringValueNode(StringUtil::unescape(val)));
}

template<typename Tree>
void printSpiritTree(std::ostream& out, Tree tree, const vespalib::string& query,
                     const DocSelectionGrammar& grammar) {
    using boost::spirit::classic::parser_id;

    std::map<parser_id, vespalib::string> names;
    names[parser_id(grammar.id_bool)] = "bool";
    names[parser_id(grammar.id_number)] = "number";
    names[parser_id(grammar.id_string)] = "string";
    names[parser_id(grammar.id_doctype)] = "doctype";
    names[parser_id(grammar.id_fieldname)] = "fieldname";
    names[parser_id(grammar.id_function)] = "function";
    names[parser_id(grammar.id_idarg)] = "idarg";
    names[parser_id(grammar.id_searchcolumnarg)] = "searchcolumnarg";
    names[parser_id(grammar.id_operator)] = "operator";
    names[parser_id(grammar.id_idspec)] = "idspec";
    names[parser_id(grammar.id_searchcolumnspec)] = "searchcolumnspec";
    names[parser_id(grammar.id_fieldspec)] = "fieldspec";
    names[parser_id(grammar.id_value)] = "value";
    names[parser_id(grammar.id_valuefuncadd)] = "valuefuncadd";
    names[parser_id(grammar.id_valuefuncmul)] = "valuefuncmul";
    names[parser_id(grammar.id_valuefuncmod)] = "valuefuncmod";
    names[parser_id(grammar.id_valuegroup)] = "valuegroup";
    names[parser_id(grammar.id_arithmvalue)] = "arithmvalue";
    names[parser_id(grammar.id_comparison)] = "comparison";
    names[parser_id(grammar.id_leaf)] = "leaf";
    names[parser_id(grammar.id_not)] = "not";
    names[parser_id(grammar.id_and)] = "and";
    names[parser_id(grammar.id_or)] = "or";
    names[parser_id(grammar.id_group)] = "group";
    names[parser_id(grammar.id_expression)] = "expression";
    tree_to_xml(out, tree, query.c_str(), names);
}

template<typename Parser>
bool testExpr(const DocumentTypeRepo& repo,
              const BucketIdFactory& factory,
              const vespalib::string& expression, const Parser& parser,
              const vespalib::string& result)
{
    //std::cerr << "Testing expression '" << expression << "'.\n";
    using boost::spirit::classic::space_p;

    DocSelectionGrammar grammar(repo, factory);
    boost::spirit::classic::tree_parse_info<> info;
    info = pt_parse(expression.c_str(), parser,
                    space_p);
    std::ostringstream ost;
    printSpiritTree(ost, info.trees, expression, grammar);
    if (!info.full) {
        cerr << "Expression '" << expression
             << "' wasn't completely parsed\n"
             << ost.str() << "\n";
        return false;
    }
    vespalib::string httpexpr = expression;
    vespalib::string::size_type index;
    while ((index = httpexpr.find('>')) != vespalib::string::npos) {
        httpexpr = httpexpr.substr(0,index) + "&gt;"
                   + httpexpr.substr(index+1);
    }
    vespalib::string fullresult = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                        "<!DOCTYPE parsetree SYSTEM \"parsetree.dtd\">\n"
                        "<!-- " + httpexpr + " -->\n" + result;
    //if (ost.str() != fullresult) {
    if (fullresult != ost.str()) {
        cerr << "Parsing expression '" << expression << "', expected\n"
             << fullresult << "\nbut got\n" << ost.str() << "\n";
        return false;
    }
    return true;
}

bool test(const DocumentTypeRepo& repo,
          const BucketIdFactory& bucketIdFactory)
{
    //std::cerr << "\n\nTESTING DOCUMENT SELECT PARSER\n\n";
    DocSelectionGrammar grammar(repo, bucketIdFactory);

    using boost::spirit::classic::space_p;

    // Parser two is the arithmvalue..
    // idspec, fieldspec, number & stringval, + - * / % ()
    testExpr(repo, bucketIdFactory, "3.14", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"number\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <value>3.14</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "-999", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"number\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <value>-999</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "15e4", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"number\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <value>15e4</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "3.4e-4", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"number\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <value>3.4e-4</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "\" Test \"", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"string\">\n"
             "                <parsenode rule=\"string\">\n"
             "                    <value> Test </value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "id", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"idspec\">\n"
             "                <parsenode rule=\"idspec\">\n"
             "                    <value>id</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "id.namespace",
             grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"idspec\">\n"
             "                <parsenode rule=\"idspec\">\n"
             "                    <value>id</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"idarg\">\n"
             "                    <parsenode rule=\"idarg\">\n"
             "                        <value>namespace</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "id.hash()", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"idspec\">\n"
             "                <parsenode rule=\"idspec\">\n"
             "                    <value>id</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"function\">\n"
             "                <parsenode rule=\"function\">\n"
             "                    <value>hash</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "id.namespace.hash()", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"idspec\">\n"
             "                <parsenode rule=\"idspec\">\n"
             "                    <value>id</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"idarg\">\n"
             "                    <parsenode rule=\"idarg\">\n"
             "                        <value>namespace</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"function\">\n"
             "                <parsenode rule=\"function\">\n"
             "                    <value>hash</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "music.artist", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"fieldspec\">\n"
             "                <parsenode rule=\"doctype\">\n"
             "                    <parsenode rule=\"doctype\">\n"
             "                        <value>music</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"fieldname\">\n"
             "                    <parsenode rule=\"fieldname\">\n"
             "                        <value>artist</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "music.artist.lowercase()", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"value\">\n"
             "            <parsenode rule=\"fieldspec\">\n"
             "                <parsenode rule=\"doctype\">\n"
             "                    <parsenode rule=\"doctype\">\n"
             "                        <value>music</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"fieldname\">\n"
             "                    <parsenode rule=\"fieldname\">\n"
             "                        <value>artist</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"function\">\n"
             "                    <parsenode rule=\"function\">\n"
             "                        <value>lowercase</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "(43)", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"valuegroup\">\n"
             "            <parsenode rule=\"arithmvalue\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>43</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "1 + 2 * 3 - 10 % 2 / 3", grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"valuefuncadd\">\n"
             "            <parsenode rule=\"value\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <value>1</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncadd\">\n"
             "                <value>+</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncmul\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>2</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"valuefuncmul\">\n"
             "                    <value>*</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>3</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncadd\">\n"
             "                <value>-</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncmul\">\n"
             "                <parsenode rule=\"valuefuncmod\">\n"
             "                    <parsenode rule=\"value\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <parsenode rule=\"number\">\n"
             "                                <value>10</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                    <parsenode rule=\"valuefuncmod\">\n"
             "                        <value>%</value>\n"
             "                    </parsenode>\n"
             "                    <parsenode rule=\"value\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <parsenode rule=\"number\">\n"
             "                                <value>2</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"valuefuncmul\">\n"
             "                    <value>/</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>3</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "(43 + 14) / 34",
             grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"valuefuncmul\">\n"
             "            <parsenode rule=\"valuegroup\">\n"
             "                <parsenode rule=\"arithmvalue\">\n"
             "                    <parsenode rule=\"valuefuncadd\">\n"
             "                        <parsenode rule=\"value\">\n"
             "                            <parsenode rule=\"number\">\n"
             "                                <parsenode rule=\"number\">\n"
             "                                    <value>43</value>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"valuefuncadd\">\n"
             "                            <value>+</value>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"value\">\n"
             "                            <parsenode rule=\"number\">\n"
             "                                <parsenode rule=\"number\">\n"
             "                                    <value>14</value>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncmul\">\n"
             "                <value>/</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"value\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <value>34</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "34 * (3 - 1) % 4",
             grammar.use_parser<2>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"arithmvalue\">\n"
             "        <parsenode rule=\"valuefuncmul\">\n"
             "            <parsenode rule=\"value\">\n"
             "                <parsenode rule=\"number\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <value>34</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncmul\">\n"
             "                <value>*</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"valuefuncmod\">\n"
             "                <parsenode rule=\"valuegroup\">\n"
             "                    <parsenode rule=\"arithmvalue\">\n"
             "                        <parsenode rule=\"valuefuncadd\">\n"
             "                            <parsenode rule=\"value\">\n"
             "                                <parsenode rule=\"number\">\n"
             "                                    <parsenode rule=\"number\">\n"
             "                                        <value>3</value>\n"
             "                                    </parsenode>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                            <parsenode rule=\"valuefuncadd\">\n"
             "                                <value>-</value>\n"
             "                            </parsenode>\n"
             "                            <parsenode rule=\"value\">\n"
             "                                <parsenode rule=\"number\">\n"
             "                                    <parsenode rule=\"number\">\n"
             "                                        <value>1</value>\n"
             "                                    </parsenode>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"valuefuncmod\">\n"
             "                    <value>%</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>4</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");

    // Parser 1 is a leaf. bool, comparison, fieldspec, doctype
    testExpr(repo, bucketIdFactory, "true", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"bool\">\n"
             "            <parsenode rule=\"bool\">\n"
             "                <value>true</value>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "false", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"bool\">\n"
             "            <parsenode rule=\"bool\">\n"
             "                <value>false</value>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "music.test", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"fieldspec\">\n"
             "            <parsenode rule=\"doctype\">\n"
             "                <parsenode rule=\"doctype\">\n"
             "                    <value>music</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"fieldname\">\n"
             "                <parsenode rule=\"fieldname\">\n"
             "                    <value>test</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory, "music", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"doctype\">\n"
             "            <parsenode rule=\"doctype\">\n"
             "                <value>music</value>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "music.artist = \"*john*\"", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"comparison\">\n"
             "            <parsenode rule=\"arithmvalue\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"fieldspec\">\n"
             "                        <parsenode rule=\"doctype\">\n"
             "                            <parsenode rule=\"doctype\">\n"
             "                                <value>music</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"fieldname\">\n"
             "                            <parsenode rule=\"fieldname\">\n"
             "                                <value>artist</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"operator\">\n"
             "                <parsenode rule=\"operator\">\n"
             "                    <value>=</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"arithmvalue\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"string\">\n"
             "                        <parsenode rule=\"string\">\n"
             "                            <value>*john*</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "music.length >= 180", grammar.use_parser<1>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"leaf\">\n"
             "        <parsenode rule=\"comparison\">\n"
             "            <parsenode rule=\"arithmvalue\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"fieldspec\">\n"
             "                        <parsenode rule=\"doctype\">\n"
             "                            <parsenode rule=\"doctype\">\n"
             "                                <value>music</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"fieldname\">\n"
             "                            <parsenode rule=\"fieldname\">\n"
             "                                <value>length</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"operator\">\n"
             "                <parsenode rule=\"operator\">\n"
             "                    <value>&gt;=</value>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"arithmvalue\">\n"
             "                <parsenode rule=\"value\">\n"
             "                    <parsenode rule=\"number\">\n"
             "                        <parsenode rule=\"number\">\n"
             "                            <value>180</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");

    // Parser 0 - The whole expression
    testExpr(repo, bucketIdFactory,
             "true oR nOt false And true", grammar.use_parser<0>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"expression\">\n"
             "        <parsenode rule=\"or\">\n"
             "            <parsenode rule=\"leaf\">\n"
             "                <parsenode rule=\"bool\">\n"
             "                    <parsenode rule=\"bool\">\n"
             "                        <value>true</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"or\">\n"
             "                <value>oR</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"and\">\n"
             "                <parsenode rule=\"not\">\n"
             "                    <parsenode rule=\"not\">\n"
             "                        <value>nOt</value>\n"
             "                    </parsenode>\n"
             "                    <parsenode rule=\"leaf\">\n"
             "                        <parsenode rule=\"bool\">\n"
             "                            <parsenode rule=\"bool\">\n"
             "                                <value>false</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"and\">\n"
             "                    <value>And</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"leaf\">\n"
             "                    <parsenode rule=\"bool\">\n"
             "                        <parsenode rule=\"bool\">\n"
             "                            <value>true</value>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "(true oR false) aNd true", grammar.use_parser<0>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"expression\">\n"
             "        <parsenode rule=\"and\">\n"
             "            <parsenode rule=\"group\">\n"
             "                <parsenode rule=\"or\">\n"
             "                    <parsenode rule=\"leaf\">\n"
             "                        <parsenode rule=\"bool\">\n"
             "                            <parsenode rule=\"bool\">\n"
             "                                <value>true</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                    <parsenode rule=\"or\">\n"
             "                        <value>oR</value>\n"
             "                    </parsenode>\n"
             "                    <parsenode rule=\"leaf\">\n"
             "                        <parsenode rule=\"bool\">\n"
             "                            <parsenode rule=\"bool\">\n"
             "                                <value>false</value>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"and\">\n"
             "                <value>aNd</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"leaf\">\n"
             "                <parsenode rule=\"bool\">\n"
             "                    <parsenode rule=\"bool\">\n"
             "                        <value>true</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    testExpr(repo, bucketIdFactory,
             "iddoc or not(notand and ornot)", grammar.use_parser<0>(),
             "<parsetree version=\"1.0\">\n"
             "    <parsenode rule=\"expression\">\n"
             "        <parsenode rule=\"or\">\n"
             "            <parsenode rule=\"leaf\">\n"
             "                <parsenode rule=\"doctype\">\n"
             "                    <parsenode rule=\"doctype\">\n"
             "                        <value>iddoc</value>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"or\">\n"
             "                <value>or</value>\n"
             "            </parsenode>\n"
             "            <parsenode rule=\"not\">\n"
             "                <parsenode rule=\"not\">\n"
             "                    <value>not</value>\n"
             "                </parsenode>\n"
             "                <parsenode rule=\"group\">\n"
             "                    <parsenode rule=\"and\">\n"
             "                        <parsenode rule=\"leaf\">\n"
             "                            <parsenode rule=\"doctype\">\n"
             "                                <parsenode rule=\"doctype\">\n"
             "                                    <value>notand</value>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"and\">\n"
             "                            <value>and</value>\n"
             "                        </parsenode>\n"
             "                        <parsenode rule=\"leaf\">\n"
             "                            <parsenode rule=\"doctype\">\n"
             "                                <parsenode rule=\"doctype\">\n"
             "                                    <value>ornot</value>\n"
             "                                </parsenode>\n"
             "                            </parsenode>\n"
             "                        </parsenode>\n"
             "                    </parsenode>\n"
             "                </parsenode>\n"
             "            </parsenode>\n"
             "        </parsenode>\n"
             "    </parsenode>\n"
             "</parsetree>\n");
    return true;
}

}

vespalib::Lock Parser::_G_parseLock;

unique_ptr<Node> Parser::parse(const vespalib::stringref & s)
{

    simple::SelectionParser simple(_bucketIdFactory);
    if (simple.parse(s) && simple.getRemaining().empty()) {
        Node::UP tmp(simple.getNode());
        assert(tmp.get() != NULL);
        return tmp;
    } else {
        return fullParse(s);
    }
}

unique_ptr<Node> Parser::fullParse(const vespalib::stringref & s)
{
    static bool haveTested = test(_repo, _bucketIdFactory); if (haveTested) {}
    try{
        vespalib::LockGuard guard(_G_parseLock);
        DocSelectionGrammar grammar(_repo, _bucketIdFactory);
        boost::spirit::classic::tree_parse_info<> info
            = pt_parse(&s[0], &s[0]+s.size(),
                       grammar.use_parser<0>(), boost::spirit::classic::space_p);
        if (!info.full) {
            vespalib::string unexpected(info.stop);
            unsigned int position = s.size() - unexpected.size();
            if (unexpected.size() > 10) {
                unexpected = unexpected.substr(0,10);
            }
            vespalib::asciistream ost;
            ost << "Unexpected token at position " << position << " ('"
                << unexpected << "') in query '" << s << "',";
            throw ParsingFailedException(ost.str(), VESPA_STRLOC);
        }
        parse_assert(info.trees.size() == 1);
        //printSpiritTree(std::cerr, info.trees, s, grammar);
        return parseTree(grammar, info.trees[0]);
    } catch (ParsingFailedException& e) {
        throw;
    } catch (vespalib::Exception& e) {
        throw ParsingFailedException("Parsing failed. See cause exception.",
                                     e, VESPA_STRLOC);
    } catch (std::exception& e) {
        cerr << "Parser::parse() internal error: "
             << e.what() << endl;
        throw; // Program will abort when this tries to go out..
    }
    return unique_ptr<Node>();
}

} // select
} // document
