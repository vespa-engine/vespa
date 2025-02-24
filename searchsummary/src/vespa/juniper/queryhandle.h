// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

/* Juniper internal interface */

class Matcher;
class MatchObject;

#include "queryvisitor.h"
#include <vector>

using queryterm_vector = std::vector<QueryTerm*>;
using querynode_vector = std::vector<QueryNode*>;

namespace juniper {

/** Juniper internal definition of the query handle
 *  The query handle keeps a (default) match object for that query
 *  and possibly a set of additional match objects for expanded queries
 *  based on available expanders.
 *  Which matchobject to use for a result is then determined
 *  by the language ID.
 */

class QueryHandle {
public:
    QueryHandle(const IQuery& fquery, const char* options);
    ~QueryHandle();

    void        SetSimpleQuery(Matcher* m);
    inline void SetPrivileged(bool priv) { _privileged_port = priv; }
    inline bool Privileged() { return _privileged_port; }
    inline void SetLog(uint32_t mask) { _log_mask = mask; }

    /** Find the currect match object to use for this query */
    MatchObject* MatchObj();

protected:
    void parse_parameters(const char* options);

private:
    MatchObject* _mo; // The default MatcherObject
    bool         _privileged_port;

    QueryHandle(QueryHandle&);
    QueryHandle& operator=(QueryHandle&);

public:
    // optional per query parameter override settings
    // (default (-1) means use configured value, other value forces override)
    int             _dynsum_len;
    int             _max_matches;
    int             _surround_max;
    int             _stem_extend;
    int             _stem_min;
    int64_t         _winsize;
    double          _winsize_fallback_multiplier;
    int64_t         _max_match_candidates;

    // parameter settings that are taken directly from
    // this handle (eg. not overrides for config settings)
    uint32_t _log_mask;
    int      _options;        // query constraint bitmap as defined in querynode.h
    int      _limit;          // WITHIN/NEAR limit by parameter
};

void SetDebug(unsigned int mask);

} // end namespace juniper
