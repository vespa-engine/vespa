// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>
#include <string>

namespace documentapi {

class TestAndSetMessage : public DocumentMessage {
private:
    TestAndSetCondition _condition;

public:
    void setCondition(const TestAndSetCondition & condition) { _condition = condition; }
    const TestAndSetCondition & getCondition() const { return _condition; }
};

}

