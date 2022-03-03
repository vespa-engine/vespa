// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replay_feedtoken_state.h"

namespace proton::feedtoken {

ReplayState::ReplayState(vespalib::SharedOperationThrottler::Token throttler_token, const FeedOperation& op)
    : IState(),
      _throttler_token(std::move(throttler_token)),
      _type(op.getType()),
      _serial_num(op.getSerialNum())
{
}

ReplayState::~ReplayState() = default;

bool
ReplayState::is_replay() const noexcept
{
    return true;
}

void
ReplayState::fail()
{
}

void
ReplayState::setResult(ResultUP, bool)
{
}

const storage::spi::Result&
ReplayState::getResult()
{
    abort();
}

}
