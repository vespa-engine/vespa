// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_term_simple.h"
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vector>

namespace search {

/**
 * Query term that can be returned in UCS-4 encoded form.
 */
class QueryTermUCS4 : public QueryTermSimple {
public:
    typedef std::vector<ucs4_t> UCS4StringT;
    typedef std::unique_ptr<QueryTermUCS4> UP;
    QueryTermUCS4(const QueryTermUCS4 &) = default;
    QueryTermUCS4 & operator = (const QueryTermUCS4 &) = default;
    QueryTermUCS4(QueryTermUCS4 &&) = default;
    QueryTermUCS4 & operator = (QueryTermUCS4 &&) = default;
    QueryTermUCS4();
    QueryTermUCS4(const string & term_, SearchTerm type);
    ~QueryTermUCS4();
    size_t getTermLen() const { return _cachedTermLen; }
    size_t term(const char * & t)     const { t = getTerm(); return _cachedTermLen; }
    UCS4StringT getUCS4Term() const;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    size_t term(const ucs4_t * & t) {
        if (_termUCS4.empty()) {
            _termUCS4 = getUCS4Term();
        }
        t = &_termUCS4[0];
        return _cachedTermLen;
    }
private:
    size_t                       _cachedTermLen;
    UCS4StringT                  _termUCS4;
};

}

