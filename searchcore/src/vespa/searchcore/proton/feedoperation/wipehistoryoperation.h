// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/fastos/timestamp.h>

namespace proton {

class WipeHistoryOperation : public FeedOperation {
    fastos::TimeStamp _wipeTimeLimit;

public:
    WipeHistoryOperation();
    WipeHistoryOperation(SerialNum serialNum, fastos::TimeStamp wipeTimeLimit);
    ~WipeHistoryOperation() override {}

    fastos::TimeStamp getWipeTimeLimit() const { return _wipeTimeLimit; }

    void serialize(vespalib::nbostream &str) const override;
    void deserialize(vespalib::nbostream &str, const document::DocumentTypeRepo &) override;
    vespalib::string toString() const override;
};

} // namespace proton

