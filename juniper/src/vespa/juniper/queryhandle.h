// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

/* Juniper internal interface */

class Matcher;
class MatchObject;

#include <vector>
#include "queryvisitor.h"
#include "expcache.h"

typedef std::vector<QueryTerm*> queryterm_vector;
typedef std::vector<QueryNode*> querynode_vector;

namespace juniper
{

/** Juniper internal definition of the query handle
 *  The query handle keeps a (default) match object for that query
 *  and possibly a set of additional match objects for expanded queries
 *  based on available expanders.
 *  Which matchobject to use for a result is then determined
 *  by the language ID.
 */

class QueryHandle
{
public:
    QueryHandle(const IQuery& fquery, const char* options, QueryModifier & modifier);
    ~QueryHandle();

    void SetSimpleQuery(Matcher* m);
    inline void SetPrivileged(bool priv) { _privileged_port = priv; }
    inline bool Privileged() { return _privileged_port; }
    inline void SetLog(uint32_t mask) { _log_mask = mask; }

    /** Find the currect match object to use for this language and query */
    MatchObject* MatchObj(uint32_t langid);

    /** Inform handle that there are expansions */
    void SetExpansions();
    /** Inform handle that there are reductions */
    void SetReductions();
protected:
    void parse_parameters(const char* options);
private:
    MatchObject* _mo; // The default MatcherObject
    bool _privileged_port;

    QueryHandle(QueryHandle &);
    QueryHandle &operator=(QueryHandle &);
public:
    // optional per query parameter override settings
    // (default (-1) means use configured value, other value forces override)
    int _dynsum_len;
    int _max_matches;
    int _surround_max;
    int _stem_extend;
    int _stem_min;
    int64_t _winsize;
    double _winsize_fallback_multiplier;
    int64_t _max_match_candidates;
    std::string _querytext; // an optional query string to use to override the input query
    ExpansionCache* _expansion_cache;

    // parameter settings that are taken directly from
    // this handle (eg. not overrides for config settings)
    uint32_t _log_mask;
    int _options;   // query constraint bitmap as defined in querynode.h
    int _limit;     // WITHIN/NEAR limit by parameter
    bool _has_expansions; // If set, the query must be replaced by a language dependent expansion (?)
    bool _has_reductions;
};

void SetDebug(unsigned int mask);

}  // end namespace juniper


