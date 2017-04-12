// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/fastos/timestamp.h>

namespace proton {

class WipeHistoryOperation : public FeedOperation {
    fastos::TimeStamp _wipeTimeLimit;

public:
    WipeHistoryOperation();
    WipeHistoryOperation(SerialNum serialNum, fastos::TimeStamp wipeTimeLimit);
    virtual ~WipeHistoryOperation() {}

    fastos::TimeStamp getWipeTimeLimit() const { return _wipeTimeLimit; }

    virtual void serialize(vespalib::nbostream &str) const override;
    virtual void deserialize(vespalib::nbostream &str,
                             const document::DocumentTypeRepo &) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

