// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feedtoken.h"
#include <vespa/vespalib/util/shared_operation_throttler.h>

namespace proton::feedtoken {

/*
 * Feed token state used during replay. It contains a throttler token
 * which allows the related shared operation throttler to track the completion
 * of the feed operation.
 */
class ReplayState : public IState {
    vespalib::SharedOperationThrottler::Token _throttler_token;
public:
    ~ReplayState() override;
    ReplayState(vespalib::SharedOperationThrottler::Token throttler_token);
    bool is_replay() const noexcept override;
    void fail() override;
    void setResult(ResultUP result, bool documentWasFound) override;
    const storage::spi::Result &getResult() override;
};

}
