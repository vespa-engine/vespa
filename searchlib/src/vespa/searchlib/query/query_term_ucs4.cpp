// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_ucs4.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/text/utf8.h>

namespace search {

QueryTermUCS4::UCS4StringT
QueryTermUCS4::getUCS4Term() const {
    UCS4StringT ucs4;
    const string & term = getTermString();
    ucs4.reserve(term.size() + 1);
    vespalib::Utf8Reader r(term);
    while (r.hasMore()) {
        ucs4_t u = r.getChar();
        ucs4.push_back(u);
    }
    ucs4.push_back(0);
    return ucs4;
}

QueryTermUCS4::QueryTermUCS4() :
    QueryTermSimple(),
    _cachedTermLen(0),
    _termUCS4()
{
    _termUCS4.push_back(0);
}

QueryTermUCS4::~QueryTermUCS4() = default;

QueryTermUCS4::QueryTermUCS4(const string & termS, SearchTerm type) :
    QueryTermSimple(termS, type),
    _cachedTermLen(0),
    _termUCS4()
{
    vespalib::Utf8Reader r(termS);
    while (r.hasMore()) {
        ucs4_t u = r.getChar();
        (void) u;
        _cachedTermLen++;
    }
}

void
QueryTermUCS4::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    QueryTermSimple::visitMembers(visitor);
    visit(visitor, "termlength", static_cast<uint64_t>(_cachedTermLen));
}

}
