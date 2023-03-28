// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include "aggregationresult.h"

namespace search::aggregation {

class VdsHit : public Hit
{
public:
    using Summary = std::vector<uint8_t>;
    using DocId = vespalib::string;
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, VdsHit);
    DECLARE_NBO_SERIALIZE;
    VdsHit() noexcept : Hit(), _docId(), _summary() {}
    VdsHit(DocId docId, HitRank rank) noexcept : Hit(rank), _docId(docId), _summary() {}
    ~VdsHit();
    VdsHit *clone() const override { return new VdsHit(*this); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const   DocId &   getDocId() const noexcept { return _docId; }
    const Summary & getSummary() const noexcept { return _summary; }
    VdsHit &   setDocId(DocId & docId) noexcept { _docId = docId; return *this; }
    VdsHit & setSummary(const void * buf, size_t sz) noexcept {
        const uint8_t * v(static_cast<const uint8_t *>(buf));
        Summary n(v, v+sz);
        _summary.swap(n);
        return *this;
    }
    bool operator < (const VdsHit &b) const noexcept { return cmp(b) < 0; }

private:
    DocId     _docId;
    Summary   _summary;
};

}
