// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8flexiblestringfieldsearcher.h"
#include <vespa/searchlib/query/streaming/fuzzy_term.h>
#include <vespa/searchlib/query/streaming/regexp_term.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.searcher.utf8flexiblestringfieldsearcher");

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
UTF8FlexibleStringFieldSearcher::duplicate() const
{
    return std::make_unique<UTF8FlexibleStringFieldSearcher>(*this);
}

size_t
UTF8FlexibleStringFieldSearcher::matchTerms(const FieldRef & f, const size_t mintsz)
{
    (void) mintsz;
    size_t words = 0;
    for (auto qt : _qtl) {
        words = matchTerm(f, *qt);
    }
    return words;
}

size_t
UTF8FlexibleStringFieldSearcher::match_regexp(const FieldRef & f, search::streaming::QueryTerm & qt)
{
    auto* regexp_term = qt.as_regexp_term();
    assert(regexp_term != nullptr);
    if (regexp_term->regexp().partial_match({f.data(), f.size()})) {
        addHit(qt, 0);
    }
    return 1;
}

size_t
UTF8FlexibleStringFieldSearcher::match_fuzzy(const FieldRef & f, search::streaming::QueryTerm & qt)
{
    auto* fuzzy_term = qt.as_fuzzy_term();
    assert(fuzzy_term != nullptr);
    // TODO delegate to matchTermExact if max edits == 0?
    //  - needs to avoid folding to have consistent normalization semantics
    if (fuzzy_term->is_match({f.data(), f.size()})) {
        addHit(qt, 0);
    }
    return 1;
}

size_t
UTF8FlexibleStringFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
    if (qt.isPrefix()) {
        LOG(debug, "Use prefix match for prefix term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return matchTermRegular(f, qt);
    } else if (qt.isSubstring()) {
        LOG(debug, "Use substring match for substring term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return matchTermSubstring(f, qt);
    } else if (qt.isSuffix()) {
        LOG(debug, "Use suffix match for suffix term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return matchTermSuffix(f, qt);
    } else if (qt.isExactstring()) {
        LOG(debug, "Use exact match for exact term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return matchTermExact(f, qt);
    } else if (qt.isRegex()) {
        LOG(debug, "Use regexp match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return match_regexp(f, qt);
    } else if (qt.isFuzzy()) {
        LOG(debug, "Use fuzzy match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
        return match_fuzzy(f, qt);
    } else {
        if (substring()) {
            LOG(debug, "Use substring match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
            return matchTermSubstring(f, qt);
        } else if (suffix()) {
            LOG(debug, "Use suffix match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
            return matchTermSuffix(f, qt);
        } else if (exact()) {
            LOG(debug, "Use exact match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
            return matchTermExact(f, qt);
        } else {
            LOG(debug, "Use regular/prefix match for term '%s:%s'", qt.index().c_str(), qt.getTerm());
            return matchTermRegular(f, qt);
        }
    }
}

UTF8FlexibleStringFieldSearcher::UTF8FlexibleStringFieldSearcher(FieldIdT fId) :
    UTF8StringFieldSearcherBase(fId)
{ }

}
