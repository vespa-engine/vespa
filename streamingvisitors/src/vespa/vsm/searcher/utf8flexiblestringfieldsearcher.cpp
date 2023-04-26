// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8flexiblestringfieldsearcher.h"

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

UTF8FlexibleStringFieldSearcher::UTF8FlexibleStringFieldSearcher() :
    UTF8StringFieldSearcherBase()
{ }

UTF8FlexibleStringFieldSearcher::UTF8FlexibleStringFieldSearcher(FieldIdT fId) :
    UTF8StringFieldSearcherBase(fId)
{ }

}
