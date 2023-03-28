// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include "aggregationresult.h"
#include <vespa/document/base/globalid.h>

namespace search::aggregation {

class FS4Hit : public Hit
{
private:
    uint32_t _path;
    uint32_t _docId;
    document::GlobalId _globalId;
    uint32_t _distributionKey;

public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, FS4Hit);
    DECLARE_NBO_SERIALIZE;
    FS4Hit() noexcept : Hit(), _path(0), _docId(0), _globalId(), _distributionKey(-1) {}
    FS4Hit(DocId docId, HitRank rank) noexcept
        : Hit(rank), _path(0), _docId(docId), _globalId(), _distributionKey(-1) {}
    FS4Hit *clone() const override { return new FS4Hit(*this); }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    uint32_t getPath() const noexcept { return _path; }
    uint32_t getDocId() const noexcept { return _docId; }
    const document::GlobalId & getGlobalId() const noexcept { return _globalId; }
    FS4Hit &setGlobalId(const document::GlobalId & globalId) noexcept { _globalId = globalId; return *this; }
    FS4Hit &setDistributionKey(uint32_t val) noexcept { _distributionKey = val; return *this; }
    bool operator < (const FS4Hit &b) const { return cmp(b) < 0; }
};

}
