// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"

namespace search::aggregation {

class VdsHit : public Hit
{
public:
    using Summary = std::vector<uint8_t>;
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, VdsHit);
    DECLARE_NBO_SERIALIZE;
    VdsHit() noexcept : Hit(), _docId(), _summary() {}
    VdsHit(std::string_view docId, HitRank rank) noexcept : Hit(rank), _docId(docId), _summary() {}
    ~VdsHit() override;
    VdsHit *clone() const override { return new VdsHit(*this); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const vespalib::string &   getDocId() const noexcept { return _docId; }
    const Summary & getSummary() const noexcept { return _summary; }
    VdsHit & setDocId(std::string_view docId) noexcept { _docId = docId; return *this; }
    VdsHit & setSummary(const void * buf, size_t sz) noexcept {
        const auto * v(static_cast<const uint8_t *>(buf));
        Summary n(v, v+sz);
        _summary.swap(n);
        return *this;
    }
    bool operator < (const VdsHit &b) const noexcept { return cmp(b) < 0; }

private:
    vespalib::string  _docId;
    Summary           _summary;
};

}
