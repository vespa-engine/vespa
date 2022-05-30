// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rewriter.h"
#include "querymodifier.h"
#include "simplemap.h"

namespace juniper
{

typedef std::map<std::string, std::vector<QueryTerm*> > string_match_table;

class string_matcher
{
public:
    explicit string_matcher(Rewriter* rw) : _rewriter(rw), _table() {}

    void add_term(QueryTerm* t);

    inline string_match_table::iterator lookup(std::string& key)
    { return _table.find(key); }
    inline Rewriter* rewriter() const { return _rewriter; }
    inline string_match_table& table() { return _table; }

    inline bool operator==(const string_matcher& m) { return _rewriter == m.rewriter(); }
    std::string dump(); // for debugging
private:
    Rewriter* _rewriter;
    string_match_table _table;

    string_matcher(string_matcher &);
    string_matcher &operator=(string_matcher &);
};

class ReduceMatcher
{
public:
    ReduceMatcher();
    ~ReduceMatcher();
    string_matcher* find(Rewriter* rw);
    const std::vector<QueryTerm*>* match(uint32_t langid, const char* term, size_t len);
private:
    simplemap<Rewriter*, string_matcher*> _matchers;
};

} // end namespace juniper


