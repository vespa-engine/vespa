// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simplemap.h"
#include "query.h"
#include "rewriter.h"
#include <vespa/vespalib/stllike/string.h>
#include <string>
#include <vector>

class QueryTerm;
class QueryExpr;
class MatchObject;

// This module encapsulates the preinitialized data structure for handling
// query or document rewriting. Eg. it is configured based on AddRewriter calls
// (See external header rewriter.h) and used until system shutdown..

// Note that per query state (reducematcher.h, expcache.h) and per hit state
// (Matcher.h) is maintained elsewhere..

namespace juniper
{

class string_matcher;
class QueryModifier;

// Wrapper around supplied IRewriter that in addition offer
// the way it is configured in the system
class Rewriter
{
public:
    Rewriter(IRewriter* rewriter, bool for_query, bool for_document);
    inline bool ForQuery()    { return _for_query; }
    inline bool ForDocument() { return _for_document; }
    inline RewriteHandle* Rewrite(uint32_t langid, const char* term)
    { return _rewriter->Rewrite(langid, term); }
    inline RewriteHandle* Rewrite(uint32_t langid, const char* term, size_t len)
    { return _rewriter->Rewrite(langid, term, len); }
    inline const char* NextTerm(RewriteHandle* exp, size_t& length)
    { return _rewriter->NextTerm(exp, length); }
private:
    IRewriter* _rewriter;
    bool _for_query;
    bool _for_document;

    Rewriter(Rewriter &);
    Rewriter &operator=(Rewriter &);
};


class QueryModifier
{
public:
    QueryModifier();
    virtual ~QueryModifier();

    /** See rewriter.h for doc */
    void AddRewriter(const char* index_name, IRewriter* rewriter,
		     bool for_query, bool for_document);

    inline bool HasExpanders() { return _has_expanders; }
    inline bool HasReducers() { return _has_reducers; }
    inline bool HasRewriters() { return _has_expanders || _has_reducers; }

    /* Return any configured reducer/expander for the index, if any */
    Rewriter* FindRewriter(vespalib::stringref index_name);

    /* Delete/dereference all rewriters (needed for testing/debugging) */
    void FlushRewriters();
private:
    simplemap<std::string, Rewriter*> _rewriters;
    bool _has_expanders;
    bool _has_reducers;
};


} // end namespace juniper

