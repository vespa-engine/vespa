// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feedtoken.h"
#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>

namespace proton::feedtoken {

/*
 * Feed token state used during replay. It contains a throttler token
 * which allows the related shared operation throttler to track the completion
 * of the feed operation.
 */
class ReplayState : public IState {
    using SerialNum = search::SerialNum;
    using ThrottlerToken = vespalib::SharedOperationThrottler::Token;

    ThrottlerToken      _throttler_token;
    FeedOperation::Type _type;
    SerialNum           _serial_num;
public:
    ~ReplayState() override;
    ReplayState(ThrottlerToken throttler_token, const FeedOperation& op);
    bool is_replay() const noexcept override;
    void fail() override;
    void setResult(ResultUP result, bool documentWasFound) override;
    const storage::spi::Result &getResult() override;
};

}
