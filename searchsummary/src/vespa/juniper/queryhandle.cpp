// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryhandle.h"
#include "Matcher.h"
#include "juniperdebug.h"
#include "query.h"
#include <vespa/log/log.h>
LOG_SETUP(".juniper.queryhandle");

namespace juniper {

QueryHandle::QueryHandle(const IQuery& fquery, const char* options)
  : _mo(nullptr),
    _privileged_port(false),
    _dynsum_len(-1),
    _max_matches(-1),
    _surround_max(-1),
    _stem_extend(-1),
    _stem_min(-1),
    _winsize(-1),
    _winsize_fallback_multiplier(-1),
    _max_match_candidates(-1),
    _log_mask(0),
    _options(0),
    _limit(0) {
    QueryVisitor* vis;

    /* Parse the options parameter structure, the parameter
     * will be invalid later on so results must be stored here
     */
    parse_parameters(options);

    /* Then parse the original query */
    vis = new QueryVisitor(fquery, this);

    QueryExpr* query = vis->GetQuery();
    if (query) {
        if (LOG_WOULD_LOG(debug)) {
            std::string s;
            query->Dump(s);
            LOG(debug, "juniper::QueryHandle: stack dump: %s", s.c_str());
        }

        /* The default match object keeps a set of "compiled" data for the
         * original query (no language dependent expansion or rewriting (reduction)
         * applied)
         */
        _mo = new MatchObject(query);
    } else {
        LOG(debug, "juniper::QueryHandle: stack dump: (no stack)");
    }

    delete vis;
}

QueryHandle::~QueryHandle() {
    LOG(debug, "juniper: Deleting query handle");
    if (_mo) delete _mo;
}

MatchObject* QueryHandle::MatchObj() {
    return _mo;
}

// small utility
std::string fetchtext(char* cur, char** next) {
    *next = cur;
    while (**next != '\0' && **next != '_') (*next)++;
    std::string t(cur, *next);
    return t;
}

void QueryHandle::parse_parameters(const char* options) {
    if (!options) return;
    char* p = const_cast<char*>(options);

    LOG(debug, "juniper parameter string '%s'", options);

    // Initially check for a privileged option enable from QR server:
    if (strncmp(p, "priv.", 5) == 0) {
        p += 5;
        SetPrivileged((strtol(p, &p, 10) > 0));
    }

    // options contains a juniperoptions command string, parse it
    for (; *p != '\0';) {
        if (strncmp(p, "dynlength.", 10) == 0) {
            p += 10;
            _dynsum_len = strtol(p, &p, 0);
            LOG(debug, "Setting dynsum.length to %d", _dynsum_len);
        } else if (strncmp(p, "dynmatches.", 11) == 0) {
            p += 11;
            _max_matches = strtol(p, &p, 0);
        } else if (strncmp(p, "dynsurmax.", 10) == 0) {
            p += 10;
            _surround_max = strtol(p, &p, 0);
        } else if (strncmp(p, "near.", 5) == 0) {
            p += 5;
            _limit = strtoul(p, &p, 0);
            _options |= X_LIMIT | X_COMPLETE | X_CONSTR | X_CHKVAL;
            LOG(debug, "juniper parameter: Setting NEAR(%d)", _limit);
        } else if (strncmp(p, "within.", 7) == 0) {
            p += 7;
            _limit = strtoul(p, &p, 0);
            _options |= X_LIMIT | X_ORDERED | X_COMPLETE | X_CONSTR | X_CHKVAL;
            LOG(debug, "juniper parameter: Setting WITHIN(%d)", _limit);
        } else if (strncmp(p, "onear.", 6) == 0) {
            p += 6;
            _limit = strtoul(p, &p, 0);
            _options |= X_LIMIT | X_ORDERED | X_COMPLETE | X_CONSTR | X_CHKVAL;
            LOG(debug, "juniper parameter: Setting ONEAR(%d)", _limit);
        } else if (strncmp(p, "stemext.", 8) == 0) {
            p += 8;
            _stem_extend = strtoul(p, &p, 0);
        } else if (strncmp(p, "stemmin.", 8) == 0) {
            p += 8;
            _stem_min = strtoul(p, &p, 0);
        } else if (strncmp(p, "winsize.", 8) == 0) {
            p += 8;
            _winsize = strtoul(p, &p, 0);
        } else if (strncmp(p, "winsize_fallback_multiplier.", 28) == 0) {
            p += 28;
            _winsize_fallback_multiplier = strtoul(p, &p, 0);
        } else if (strncmp(p, "max_match_candidates.", 21) == 0) {
            p += 21;
            _max_match_candidates = strtoul(p, &p, 0);
        } else if (Privileged()) {
            if (strncmp(p, "log.", 4) == 0) {
                p += 4;
                SetLog(strtol(p, &p, 0));
            } else if (strncmp(p, "debug.", 6) == 0) {
                p += 6;
                juniper::SetDebug(strtol(p, &p, 0));
            }
        }
        while (*p != '\0' && *p != '_') p++;
        if (*p == '_') p++;
    }
} // end parse_parameters

} // namespace juniper
