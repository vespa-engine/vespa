// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8exactstringfieldsearcher.h"

using search::byte;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

UTF8ExactStringFieldSearcher::UTF8ExactStringFieldSearcher(FieldIdT fId)
    : UTF8StringFieldSearcherBase(fId)
{
    match_type(EXACT);
    normalize_mode(Normalizing::LOWERCASE);
}

std::unique_ptr<FieldSearcher>
UTF8ExactStringFieldSearcher::duplicate() const
{
    return std::make_unique<UTF8ExactStringFieldSearcher>(*this);
}

size_t
UTF8ExactStringFieldSearcher::matchTerms(const FieldRef & f, size_t mintsz)
{
    (void) mintsz;
    for (auto qt : _qtl) {
        matchTermExact(f, *qt);
    }
    return 1;
}

size_t
UTF8ExactStringFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
    return matchTermExact(f, qt);
}

}
