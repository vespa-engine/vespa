// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

class CompactLidSpaceOperation : public FeedOperation
{
private:
    uint32_t _subDbId;
    uint32_t _lidLimit;

public:
    CompactLidSpaceOperation();
    CompactLidSpaceOperation(uint32_t subDbId, uint32_t lidLimit);
    virtual ~CompactLidSpaceOperation() {}

    uint32_t getSubDbId() const { return _subDbId; }
    uint32_t getLidLimit() const { return _lidLimit; }

    // Implements FeedOperation
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

