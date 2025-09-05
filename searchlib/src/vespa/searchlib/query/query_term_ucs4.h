// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_term_simple.h"
#include <atomic>

namespace search {

/**
 * Query term that can be returned in UCS-4 encoded form.
 */
class QueryTermUCS4 : public QueryTermSimple {
public:
    using ucs4_t = uint32_t;
    QueryTermUCS4(const QueryTermUCS4 &) = delete;
    QueryTermUCS4 & operator = (const QueryTermUCS4 &) = delete;
    QueryTermUCS4(QueryTermUCS4 &&) = delete;
    QueryTermUCS4 & operator = (QueryTermUCS4 &&) = delete;
    QueryTermUCS4(const string & term_, Type type);
    QueryTermUCS4(Type type, std::unique_ptr<NumericRangeSpec> range);
    ~QueryTermUCS4() override;
    uint32_t getTermLen() const { return _cachedTermLen; }
    uint32_t term(const char * & t)     const { t = getTerm(); return _cachedTermLen; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    std::unique_ptr<ucs4_t[]> asUcs4() const;
    uint32_t term(const ucs4_t * & t) {
        t = _termUCS4.load(std::memory_order_relaxed);
        if (t == nullptr) {
            t = fillUCS4();
        }
        return _cachedTermLen;
    }
private:
    const ucs4_t * fillUCS4();
    std::atomic<ucs4_t *>  _termUCS4;
    uint32_t               _cachedTermLen;
};

}
