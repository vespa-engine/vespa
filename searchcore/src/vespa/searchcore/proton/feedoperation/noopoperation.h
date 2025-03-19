// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

struct NoopOperation : FeedOperation {
    NoopOperation() : FeedOperation(FeedOperation::NOOP) {}
    NoopOperation(SerialNum serialNum);
    ~NoopOperation() override = default;

    void serialize(vespalib::nbostream &) const override {}
    void deserialize(vespalib::nbostream &,
                     const document::DocumentTypeRepo &) override {}
    std::string toString() const override;
};

} // namespace proton

