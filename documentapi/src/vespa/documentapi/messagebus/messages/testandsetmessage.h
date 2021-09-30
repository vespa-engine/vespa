// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include "documentmessage.h"
#include "testandsetcondition.h"

namespace documentapi {

class TestAndSetMessage : public DocumentMessage {
private:
    TestAndSetCondition _condition;

public:
    TestAndSetMessage();
    ~TestAndSetMessage() override;
    void setCondition(const TestAndSetCondition & condition) { _condition = condition; }
    const TestAndSetCondition & getCondition() const { return _condition; }
};

}

