// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

struct NoopOperation : FeedOperation {
    NoopOperation() : FeedOperation(FeedOperation::NOOP) {}
    NoopOperation(SerialNum serialNum);
    virtual ~NoopOperation() {}

    virtual void serialize(vespalib::nbostream &) const {}
    virtual void deserialize(vespalib::nbostream &,
                             const document::DocumentTypeRepo &) {}
    virtual vespalib::string toString() const;
};

} // namespace proton

