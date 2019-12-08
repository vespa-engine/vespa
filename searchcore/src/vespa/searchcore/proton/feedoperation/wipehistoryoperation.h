// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

class WipeHistoryOperation : public FeedOperation {
    int64_t _wipeTimeLimit;

public:
    WipeHistoryOperation();
    WipeHistoryOperation(SerialNum serialNum, int64_t wipeTimeLimit);
    ~WipeHistoryOperation() override {}

    void serialize(vespalib::nbostream &str) const override;
    void deserialize(vespalib::nbostream &str, const document::DocumentTypeRepo &) override;
    vespalib::string toString() const override;
};

} // namespace proton

