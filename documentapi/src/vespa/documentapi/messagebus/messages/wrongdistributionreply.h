// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"

namespace documentapi {

class WrongDistributionReply : public DocumentReply {
private:
    string _systemState;

public:
    using UP = std::unique_ptr<WrongDistributionReply>;
    using SP = std::shared_ptr<WrongDistributionReply>;

    WrongDistributionReply();
    explicit WrongDistributionReply(string systemState);
    ~WrongDistributionReply() override;
    const string &getSystemState() const { return _systemState; };
    void setSystemState(const string &state) { _systemState = state; };
    string toString() const override { return "wrongdistributionreply"; }
};

}
