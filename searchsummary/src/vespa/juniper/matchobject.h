// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryhandle.h"
#include "querynode.h"
#include "hashbase.h"
#include <vespa/fastlib/text/unicodeutil.h>
#include "reducematcher.h"
#include "ITokenProcessor.h"

typedef juniper::Result Result;
typedef ITokenProcessor::Token Token;

// Reverse length order, longest match first - needed to allow matcher to
// match on the most explicit matches before the more implicit ones
// Quick hack for setting up matchobject (which depend on (<=)
//
struct QueryTermLengthComparator
{
    inline bool operator()(QueryTerm* m1, QueryTerm* m2)
    {
        return m1->len <= m2->len;
    }
};

typedef Fast_HashTable<ucs4_t, QueryTerm*, 0x20,
		       QueryTermLengthComparator> queryterm_hashtable;

class match_iterator
{
public:
    match_iterator(MatchObject* mo, Result* rhandle);
    QueryTerm* current();
    QueryTerm* next();
    QueryTerm* first_match(Token& token);
private:
    QueryTerm* first();
    QueryTerm* next_reduce_match();
    queryterm_hashtable& _table;
    queryterm_hashtable::element* _el;
public:
    Result* _rhandle;
private:
    bool _reductions;
    const std::vector<QueryTerm*>* _reduce_matches;
    std::vector<QueryTerm*>::const_iterator _reduce_matches_it;
    MatchObject* _mo;
    size_t _len, _stem_min, _stemext;
    const ucs4_t* _term;

    match_iterator(match_iterator &);
    match_iterator &operator=(match_iterator &);
};


// MatchObject encapsulate the data structure necessary to map from a query word to a
// unique index + options for this query.
// A MatchObject keeps no state for a particular document
// so it can be reused for later results for
// the same query/language combination.

class MatchObject
{
public:
    // Constructor for the default match object.
    // Resumes ownership of query
    MatchObject(QueryExpr* query, bool has_reductions);

    // Constructor for language specific extensions:
    // Creates a duplicate of query
    MatchObject(QueryExpr* query, bool has_reductions, uint32_t langid);

    ~MatchObject();

    typedef match_iterator iterator;

    /** Check if the given string matches any query term in the MatchObject
     * @param an iterator that will be updated to iterate over all matching query terms
     * @param term the term to match
     * @param len the length of the term
     * @param options tell if match was exact/pre/post etc.
     * @return true if a match was found (and the iterator points to the first element)
     */
    bool Match(iterator& mi, Token& token, unsigned& options);

    inline QueryTerm* Term(int idx) { return _qt[idx]; }

    inline size_t TermCount() { return _qt.size(); }
    inline size_t NontermCount() { return _nonterms.size(); }
    inline int MaxArity() { return _max_arity; }

    inline bool HasConstraints() { return (_query ? (_query->_options & X_CONSTR) : false); }
    inline bool UsesValid() { return (_query ? (_query->_options & X_CHKVAL) : false); }

    inline QueryExpr* Query() { return _query; }
    inline bool HasReductions() { return _has_reductions; }

    // internal use only..
    void add_queryterm(QueryTerm* term);
    void add_nonterm(QueryNode* n);
    void add_reduction_term(QueryTerm* term, juniper::Rewriter*);
private:
    friend class match_iterator;
    QueryExpr* _query;
    std::vector<QueryTerm*> _qt; // fast lookup by index
    std::vector<QueryNode*> _nonterms;
    bool _match_overlap;
    int _max_arity;
    bool _has_reductions; // query contains terms that reqs reduction of tokens before matching
    queryterm_hashtable _qt_byname; // fast lookup by name
    juniper::ReduceMatcher _reduce_matchers;

    MatchObject(MatchObject &);
    MatchObject &operator=(MatchObject &);
};

