// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_token.h"

namespace search {

FlushToken::FlushToken()
    : _stop(false)
{
}

FlushToken::~FlushToken() = default;

bool
FlushToken::stop_requested() const noexcept
{
    return _stop.load(std::memory_order_relaxed);
}

void
FlushToken::request_stop() noexcept
{
    _stop.store(true, std::memory_order_relaxed);
}

}
