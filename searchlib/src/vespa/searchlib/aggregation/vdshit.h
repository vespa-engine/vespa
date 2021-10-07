// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include "aggregationresult.h"
#include <vespa/vespalib/util/array.h>

namespace search::aggregation {

class VdsHit : public Hit
{
public:
    typedef vespalib::Array<uint8_t> Summary;
    typedef vespalib::string DocId;
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, VdsHit);
    DECLARE_NBO_SERIALIZE;
    VdsHit() : Hit(), _docId(), _summary() {}
    VdsHit(DocId docId, HitRank rank) : Hit(rank), _docId(docId), _summary() {}
    ~VdsHit();
    VdsHit *clone() const override { return new VdsHit(*this); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const   DocId &   getDocId() const { return _docId; }
    const Summary & getSummary() const { return _summary; }
    VdsHit &   setDocId(DocId & docId)     { _docId = docId; return *this; }
    VdsHit & setSummary(const void * buf, size_t sz) {
        const uint8_t * v(static_cast<const uint8_t *>(buf));
        Summary n(v, v+sz);
        _summary.swap(n);
        return *this;
    }
    bool operator < (const VdsHit &b) const { return cmp(b) < 0; }

private:
    DocId     _docId;
    Summary   _summary;
};

}
