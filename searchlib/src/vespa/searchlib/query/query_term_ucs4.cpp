// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_ucs4.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/text/utf8.h>
#include <mutex>

namespace search {

namespace {
    std::mutex _globalMutex;
}

QueryTermUCS4::~QueryTermUCS4() {
    ucs4_t * ucs4 = _termUCS4.load(std::memory_order_relaxed);
    if (ucs4 != nullptr) {
        delete [] ucs4;
    }
}

QueryTermUCS4::QueryTermUCS4(const string & termS, Type type) :
    QueryTermSimple(termS, type),
    _termUCS4(nullptr),
    _cachedTermLen(0)
{
    vespalib::Utf8Reader r(termS);
    while (r.hasMore()) {
        ucs4_t u = r.getChar();
        (void) u;
        _cachedTermLen++;
    }
}

const ucs4_t *
QueryTermUCS4::fillUCS4() {
    /*
     * Double checked locking......
     * This is a 'dirty' optimisation, but this is done to avoid writing a lot of data and blow the cpu caches with something
     * you do not really need most of the time. That matters when qps is very high and query is wide, and hits are few.
     */
    std::lock_guard guard(_globalMutex);
    ucs4_t * ucs4 = _termUCS4.load(std::memory_order_relaxed);
    if (ucs4 != nullptr) return ucs4;
    ucs4 = new ucs4_t[_cachedTermLen + 1];
    vespalib::Utf8Reader r(getTermString());
    uint32_t i(0);
    while (r.hasMore()) {
        ucs4[i++] = r.getChar();
    }
    ucs4[_cachedTermLen] = 0;
    _termUCS4.store(ucs4);
    return ucs4;
}

void
QueryTermUCS4::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    QueryTermSimple::visitMembers(visitor);
    visit(visitor, "termlength", static_cast<uint64_t>(_cachedTermLen));
}

}
