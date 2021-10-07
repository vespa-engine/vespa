// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


/* Simple prefix syntax advanced query parser for Juniper debug/testing */

#include "query.h"
#include <map>
#include <string>
#include <vespa/fastlib/text/latintokenizer.h>

namespace juniper
{

struct IsPunctuation {
    bool operator()(char c) {
        if (c == '*' || c == '?')
            return false;

        return ispunct(static_cast<unsigned char>(c)) != 0;
    }
};

typedef Fast_LatinTokenizer<Fast_IsSpace, IsPunctuation> WildcardTokenizer;

class QueryParser : public IQuery
{
private:
    QueryParser(const QueryParser&);
    QueryParser& operator= (const QueryParser&);
public:
    QueryParser(const char* query_string);
    virtual ~QueryParser();

    bool Traverse(IQueryVisitor* v) const override;
    int Weight(const QueryItem* item) const override;
    ItemCreator Creator(const QueryItem* item) const override;
    const char* Index(const QueryItem* item, size_t* length) const override;
    bool UsefulIndex(const QueryItem* item) const override;

    int ParseError() { return _parse_errno; }
protected:
    QueryItem* ParseExpr();
    QueryItem* ParseKeyword();
    QueryItem* ParseIndexTerm();
    QueryItem* CheckPrefix(std::string& kw);
    void next();
    void trav(QueryItem*) const;
    inline void setvisitor(IQueryVisitor* v) { _v = v; }
    bool match(const char* s, bool required = false);
private:
    typedef WildcardTokenizer Tokenizer;
    Tokenizer _tokenizer;
    std::map<std::string, int> _op_to_type;
    const char* _query_string;
    std::string _curtok;
    IQueryVisitor* _v;
    QueryItem* _exp;
    int _parse_errno;
    bool _reached_end;
};

}  // end namespace juniper

