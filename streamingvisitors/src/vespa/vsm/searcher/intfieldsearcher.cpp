// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "intfieldsearcher.h"

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
IntFieldSearcher::duplicate() const
{
    return std::make_unique<IntFieldSearcher>(*this);
}

IntFieldSearcher::IntFieldSearcher(FieldIdT fId) :
    FieldSearcher(fId),
    _intTerm()
{ }

IntFieldSearcher::~IntFieldSearcher() = default;

void IntFieldSearcher::prepare(search::streaming::QueryTermList& qtl,
                               const SharedSearcherBuf& buf,
                               const vsm::FieldPathMapT& field_paths,
                               search::fef::IQueryEnvironment& query_env)
{
    _intTerm.clear();
    FieldSearcher::prepare(qtl, buf, field_paths, query_env);
    for (auto qt : qtl) {
        size_t sz(qt->termLen());
        if (sz) {
            int64_t low;
            int64_t high;
            bool valid = qt->getAsIntegerTerm(low, high);
            _intTerm.push_back(IntInfo(low, high, valid));
        }
    }
}

void IntFieldSearcher::onValue(const document::FieldValue & fv)
{
    for(size_t j=0, jm(_intTerm.size()); j < jm; j++) {
        const IntInfo & ii = _intTerm[j];
        if (ii.valid() && (ii.cmp(fv.getAsLong()))) {
            addHit(*_qtl[j], 0);
        }
    }
    ++_words;
}

}
