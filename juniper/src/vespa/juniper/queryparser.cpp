// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
/* Simple prefix query parser for Juniper for debugging purposes */

#include "queryparser.h"
#include "juniperdebug.h"
#include <vector>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.queryparser");

#define TOK_NORM_OP    1
#define TOK_PARAM1_OP  2

namespace juniper {

// simple syntax tree

class QueryItem
{
public:
    QueryItem(const char* name, int p1 = -1) :
        _name(name), _index(""), _child(), _prefix(false), _p1(p1)
    { }

    ~QueryItem()
    {
        for (std::vector<QueryItem*>::iterator it = _child.begin(); it != _child.end(); ++it)
            delete *it;
    }

    inline int arity() { return _child.size(); }

    void add(QueryItem* e)
    {
        _child.push_back(e);
        LOG(debug, "Adding %s", e->_name.c_str());
    }

    std::string _name;
    std::string _index;
    std::vector<QueryItem*> _child;
    bool _prefix;
    int _p1;
};


QueryParser::QueryParser(const char* query_string) :
    _tokenizer(),
    _op_to_type(),
    _query_string(query_string),
    _curtok(),
    _v(NULL),
    _exp(NULL), _parse_errno(0), _reached_end(false)
{
    _op_to_type["AND"]    = TOK_NORM_OP;
    _op_to_type["OR"]     = TOK_NORM_OP;
    _op_to_type["ANY"]    = TOK_NORM_OP;
    _op_to_type["RANK"]   = TOK_NORM_OP;
    _op_to_type["ANDNOT"] = TOK_NORM_OP;
    _op_to_type["PHRASE"] = TOK_NORM_OP;
    _op_to_type["NEAR"]   = TOK_PARAM1_OP;
    _op_to_type["WITHIN"] = TOK_PARAM1_OP;
    _op_to_type["ONEAR"]  = TOK_PARAM1_OP;

    _tokenizer.SetNewText(const_cast<char*>(_query_string), strlen(_query_string));
    if (_tokenizer.MoreTokens())
    {
        next();
        _exp = ParseExpr();
        if (ParseError()) return;
    }
    else
    {
        _exp = NULL;
        _parse_errno = 1;
        return;
    }
    if (_tokenizer.MoreTokens())
    {
        LOG(warning, "juniper::QueryParser: Warning: extra token(s) after end");
        _parse_errno = 2;
    }
}

void QueryParser::next()
{
    if (_reached_end) _parse_errno = 3;
    if (!_tokenizer.MoreTokens())
    {
        _reached_end = true;
        return;
    }
    Tokenizer::Fast_Token token = _tokenizer.GetNextToken();
    _curtok.assign(token.first, token.second);
    LOG(debug, "next: %s", _curtok.c_str());
}

bool QueryParser::match(const char* s, bool required)
{
    bool m = strcmp(_curtok.c_str(), s) == 0;
    if (required && !m) {
        LOG(warning, "juniper::QueryParser: Syntax error query string \"%s\", failed to match \"%s\"",
            _query_string, s);
    }
    return m;
}


bool QueryParser::Traverse(IQueryVisitor* v) const
{
    const_cast<QueryParser*>(this)->_v = v;
    if (_exp) trav(_exp);
    return true;
}


int QueryParser::Weight(const QueryItem*) const
{
    return 100;
}

ItemCreator QueryParser::Creator(const QueryItem*) const
{
    return CREA_ORIG;
}

const char* QueryParser::Index(const QueryItem* e, size_t* len) const
{
    if (len) *len = e->_index.size();
    return e->_index.c_str();
}

bool QueryParser::UsefulIndex(const QueryItem*) const
{
    return true;
}


QueryParser::~QueryParser()
{
    delete _exp;
}


void QueryParser::trav(QueryItem* e) const
{
    if (e->arity() == 0)
        _v->VisitKeyword(e, e->_name.c_str(), e->_name.size(), e->_prefix);
    if      (e->_name.compare("AND")    == 0)  _v->VisitAND(e, e->arity());
    else if (e->_name.compare("OR")     == 0)  _v->VisitOR(e, e->arity());
    else if (e->_name.compare("ANY")    == 0)  _v->VisitANY(e, e->arity());
    else if (e->_name.compare("ANDNOT") == 0)  _v->VisitANDNOT(e, e->arity());
    else if (e->_name.compare("RANK")   == 0)  _v->VisitRANK(e, e->arity());
    else if (e->_name.compare("PHRASE") == 0)  _v->VisitPHRASE(e, e->arity());
    else if (e->_name.compare("NEAR")   == 0)  _v->VisitNEAR(e, e->arity(), e->_p1);
    else if (e->_name.compare("WITHIN") == 0)  _v->VisitWITHIN(e, e->arity(), e->_p1);
    else if (e->_name.compare("ONEAR")  == 0)  _v->VisitWITHIN(e, e->arity(), e->_p1);

    for (std::vector<QueryItem*>::iterator it = e->_child.begin(); it != e->_child.end(); ++it)
        trav(*it);
}		

QueryItem* QueryParser::ParseExpr()
{
    int p1 = -1;
    std::map<std::string, int>::iterator it = _op_to_type.find(_curtok);
    if (it == _op_to_type.end())
        return ParseIndexTerm();
    std::string op = _curtok;
    switch (it->second)
    {
    case TOK_NORM_OP:
	break;
    case TOK_PARAM1_OP:
	next();
	if (!match("/", true)) return NULL;
	next();
	p1 = atoi(_curtok.c_str());
	LOG(debug, "constraint operator %s - value %d", op.c_str(), p1);
	break;
    default:
        LOG_ABORT("should not reach here");
    }
    next();
    if (!match("(", true)) return NULL;
    QueryItem* e = new QueryItem(op.c_str(), p1);
    do
    {
        if (ParseError()) return NULL;
        next();
        QueryItem* ep = ParseExpr();
        if (!ep)
        {
            delete e;
            return NULL;
        }
        e->add(ep);
    } while (match(","));
    if (!match(")", true))
    {
        delete e;
        return NULL;
    }
    next();
    return e;
}


QueryItem* QueryParser::ParseIndexTerm()
{
    std::string t = _curtok;
    next();
    if (match(":"))
    {
        next();
        LOG(debug, "ParseIndexTerm: %s:%s", t.c_str(), _curtok.c_str());
        QueryItem* e = ParseKeyword();
        if (e) e->_index = t;
        return e;
    }
    else
        return CheckPrefix(t);
}

QueryItem* QueryParser::CheckPrefix(std::string& kw)
{
    std::string::size_type pos = kw.find_first_of("*?");
    bool prefix = pos == kw.size() - 1 && kw[pos] == '*';
    if (prefix)
        kw.erase(pos);
    QueryItem* e = new QueryItem(kw.c_str());
    e->_prefix = pos != std::string::npos;
    return e;
}


QueryItem* QueryParser::ParseKeyword()
{
    LOG(debug, "ParseKeyword: %s", _curtok.c_str());
    QueryItem* e = CheckPrefix(_curtok);
    next();
    return e;
}

}  // end namespace juniper
