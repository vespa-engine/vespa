// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "systemstate.h"
#include "nodestate.h"
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <boost/spirit/include/classic_core.hpp>
#include <boost/spirit/include/classic_parse_tree.hpp>
#include <boost/spirit/include/classic_tree_to_xml.hpp>
#include <boost/spirit/include/classic_chset.hpp>
#include <boost/spirit/include/classic_escape_char.hpp>
#include <boost/spirit/include/classic_grammar_def.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".systemstate");

using namespace documentapi;

/**
 * This class implements a boost::spirit type parser for the system state string. All contained names
 * confirm to the boost::spirit naming convention, and care should therefore be taken if one wishes
 * to modify any of these. Note that all content is inlined, just as all of boost::spirit, so this is
 * therefore contained in the .cpp file instead of a separate .h file.
 */
struct SystemStateGrammar : public boost::spirit::classic::grammar<SystemStateGrammar> {
    enum RuleId {
        id_hexChar = 1,
        id_hexCode,
        id_alphaNum,
        id_string,
        id_argument,
        id_argumentList,
        id_locationItem,
        id_location,
        id_systemState
    };

    template <typename Scanner>
    struct gram_base {
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_hexChar> >      rule_hexChar;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_hexCode> >      rule_hexCode;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_alphaNum> >     rule_alphaNum;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_string> >       rule_string;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_argument> >     rule_argument;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_argumentList> > rule_argumentList;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_locationItem> > rule_locationItem;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_location> >     rule_location;
        typedef typename boost::spirit::classic::rule<Scanner, boost::spirit::classic::parser_tag<id_systemState> >  rule_systemState;
        typedef boost::spirit::classic::grammar_def<rule_systemState> type;
    };

    template <typename Scanner>
    struct definition : gram_base<Scanner>::type {
        typename gram_base<Scanner>::rule_hexChar      _hexChar;
        typename gram_base<Scanner>::rule_hexCode      _hexCode;
        typename gram_base<Scanner>::rule_alphaNum     _alphaNum;
        typename gram_base<Scanner>::rule_string       _string;
        typename gram_base<Scanner>::rule_argument     _argument;
        typename gram_base<Scanner>::rule_argumentList _argumentList;
        typename gram_base<Scanner>::rule_locationItem _locationItem;
        typename gram_base<Scanner>::rule_location     _location;
        typename gram_base<Scanner>::rule_systemState  _systemState;

        definition(const SystemStateGrammar &) :
            _hexChar(),
            _hexCode(),
            _alphaNum(),
            _string(),
            _argument(),
            _argumentList(),
            _locationItem(),
            _location(),
            _systemState() {
            _hexChar      = ( boost::spirit::classic::chset<>("A-Fa-f0-9") );
            _hexCode      = ( boost::spirit::classic::ch_p('%') >> _hexChar >> _hexChar);
            _alphaNum     = ( boost::spirit::classic::chset<>("A-Za-z0-9") |
                              boost::spirit::classic::ch_p('-') | boost::spirit::classic::ch_p('.') |
                              boost::spirit::classic::ch_p('_') | boost::spirit::classic::ch_p('~') );
            _string       = ( +( boost::spirit::classic::ch_p('+') | _hexCode | _alphaNum ) );
            _argument     = ( _string >> boost::spirit::classic::ch_p('=') >> _string );
            _argumentList = ( _argument >> *( boost::spirit::classic::ch_p('&') >> _argument ) );
            _locationItem = ( boost::spirit::classic::str_p("..") | boost::spirit::classic::ch_p('.') | _string );
            _location     = ( !boost::spirit::classic::ch_p('/')
                              >> _locationItem
                              >> *( boost::spirit::classic::ch_p('/') >> _locationItem )
                              >> !boost::spirit::classic::ch_p('/') );
            _systemState  = ( +( *boost::spirit::classic::space_p >>
                                 _location >> !( boost::spirit::classic::ch_p('?') >> _argumentList ) ) );
            this->start_parsers(_systemState);
        }
    };
};

template<typename T> void
debugNode(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node, const string &prefix = "")
{
    std::map<boost::spirit::classic::parser_id, string> names;
    names[boost::spirit::classic::parser_id(grammar.id_hexChar)]      = "hexChar";
    names[boost::spirit::classic::parser_id(grammar.id_hexCode)]      = "hexCode";
    names[boost::spirit::classic::parser_id(grammar.id_alphaNum)]     = "alphaNum";
    names[boost::spirit::classic::parser_id(grammar.id_string)]       = "string";
    names[boost::spirit::classic::parser_id(grammar.id_argument)]     = "argument";
    names[boost::spirit::classic::parser_id(grammar.id_argumentList)] = "argumentList";
    names[boost::spirit::classic::parser_id(grammar.id_locationItem)] = "locationItem";
    names[boost::spirit::classic::parser_id(grammar.id_location)]     = "location";
    names[boost::spirit::classic::parser_id(grammar.id_systemState)]  = "systemState";

    std::cout << prefix << names[node.value.id()] << ": " << string(node.value.begin(), node.value.end()) << std::endl;
    for (size_t i = 0; i < node.children.size(); i++) {
        debugNode(grammar, node.children[i], vespalib::make_string("%s  %d.", prefix.c_str(), (int)i));
    }
}

template<typename T> string
parseHexChar(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_hexChar);
    assert(node.children.size() == 1);
    assert(node.children[0].value.id().to_long() == grammar.id_hexChar);
    (void) grammar;
    return string(node.children[0].value.begin(), node.children[0].value.end());
}

template<typename T> char
parseHexCode(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_hexCode);
    assert(node.children.size() == 3);
    assert(node.children[0].value.id().to_long() == grammar.id_hexCode);
    assert(node.children[1].value.id().to_long() == grammar.id_hexChar);
    assert(node.children[2].value.id().to_long() == grammar.id_hexChar);
    string enc = parseHexChar(grammar, node.children[1]) + parseHexChar(grammar, node.children[2]);
    return (char)strtol(enc.c_str(), NULL, 16);
}

template<typename T> string
parseAlphaNum(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_alphaNum);
    assert(node.children.size() == 1);
    assert(node.children[0].value.id().to_long() == grammar.id_alphaNum);
    (void) grammar;
    return string(node.children[0].value.begin(), node.children[0].value.end());
}

template<typename T> string
parseString(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_string);
    string ret;
    for (size_t i = 0; i < node.children.size(); ++i) {
        boost::spirit::classic::tree_node<T> &child = node.children[i];
        if (child.value.id().to_long() == grammar.id_string) {
            ret += " ";
        }
        else if (child.value.id().to_long() == grammar.id_alphaNum) {
            ret += parseAlphaNum(grammar, child);
        }
        else if (child.value.id().to_long() == grammar.id_hexCode) {
            ret += parseHexCode(grammar, child);
        }
    }
    return ret;
}

template<typename T> void
parseArgument(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node,
              std::map<string, string> &arg)
{
    assert(node.value.id().to_long() == grammar.id_argument);
    assert(node.children.size() == 3);
    assert(node.children[0].value.id().to_long() == grammar.id_string);
    assert(node.children[1].value.id().to_long() == grammar.id_argument);
    assert(node.children[2].value.id().to_long() == grammar.id_string);
    string key = parseString(grammar, node.children[0]);
    string val = parseString(grammar, node.children[2]);
    arg[key] = val;
}

template<typename T> void
parseArgumentList(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node,
                  std::map<string, string> &arg)
{
    assert(node.value.id().to_long() == grammar.id_argumentList);
    for (size_t i = 0; i < node.children.size(); ++i) {
        boost::spirit::classic::tree_node<T> &child = node.children[i];
        if (child.value.id().to_long() == grammar.id_argument) {
            parseArgument(grammar, child, arg);
        }
    }
}

template<typename T> string
parseLocationItem(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_locationItem);
    assert(node.children.size() == 1);

    string ret;
    boost::spirit::classic::tree_node<T> &child = node.children[0];
    if (child.value.id().to_long() == grammar.id_locationItem) {
        ret = string(child.value.begin(), child.value.end());
    }
    else if (child.value.id().to_long() == grammar.id_string) {
        ret = parseString(grammar, child);
    }
    return ret;
}

template<typename T> string
parseLocation(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_location);
    string ret;
    for (size_t i = 0; i < node.children.size(); ++i) {
        boost::spirit::classic::tree_node<T> &child = node.children[i];
        if (child.value.id().to_long() == grammar.id_locationItem) {
            ret += parseLocationItem(grammar, child) + "/";
        }
    }
    return ret.substr(0, ret.size() - 1);
}

template<typename T> NodeState::UP
parseSystemState(SystemStateGrammar &grammar, boost::spirit::classic::tree_node<T> &node)
{
    assert(node.value.id().to_long() == grammar.id_systemState);
    NodeState::UP ret(new NodeState());
    string loc, pwd;
    std::map<string, string> arg;
    for (size_t i = 0; i < node.children.size(); ++i) {
        boost::spirit::classic::tree_node<T> &child = node.children[i];
        if (child.value.id().to_long() == grammar.id_systemState) {
            if (string(child.value.begin(), child.value.end()) != "?") {
                if (!arg.empty()) {
                    ret->addChild(!loc.empty() ? loc : pwd, NodeState(arg));
                }
                else {
                    pwd = loc;
                }
                loc.clear();
                arg.clear();
            }
        }
        else if (child.value.id().to_long() == grammar.id_location) {
            if (!pwd.empty()) {
                loc = pwd + "/";
            }
            loc += parseLocation(grammar, child);
        }
        else if (child.value.id().to_long() == grammar.id_argumentList) {
            parseArgumentList(grammar, child, arg);
        }
    }
    if (!arg.empty()) {
        ret->addChild(!loc.empty() ? loc : pwd, NodeState(arg));
    }
    return ret;
}

namespace {
    vespalib::Lock _G_parseLock;
}

SystemState::UP
SystemState::newInstance(const string &state)
{
    if (state.empty()) {
        return SystemState::UP(new SystemState(NodeState::UP(new NodeState())));
    }
    try {
        vespalib::LockGuard guard(_G_parseLock);
        SystemStateGrammar grammar;
        boost::spirit::classic::tree_parse_info<> info =
            boost::spirit::classic::pt_parse(static_cast<const char *>(&*state.begin()),
                                    static_cast<const char *>(&*state.end()),
                                    grammar.use_parser<0>());
        if (!info.full) {
            string unexpected(info.stop);
            unsigned int position = state.size() - unexpected.size();
            if (unexpected.size() > 10) {
                unexpected = unexpected.substr(0, 10);
            }
            LOG(error, "Unexpected token at position %u ('%s') in query '%s'.",
                position, unexpected.c_str(), state.c_str());
        }
        else if (info.trees.size() != 1) {
            LOG(error, "Parser returned %u trees, expected 1.",
                (uint32_t)info.trees.size());
        }
        else {
            return SystemState::UP(new SystemState(parseSystemState(grammar, info.trees[0])));
        }
    }
    catch(std::exception& e) {
        LOG(fatal, "SystemState::parse() internal error: %s", e.what());
    }
    return SystemState::UP();
}

SystemState::SystemState(NodeState::UP root) :
    _root(std::move(root)),
    _lock(std::make_unique<vespalib::Lock>())
{}

SystemState::~SystemState() {}
