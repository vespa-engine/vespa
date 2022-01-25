// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <optional>

namespace proton {

/*
 * Policy for transaction log replay throttling. If params are set then a dynamic throttler
 * is used, otherwise an unlimited throttler is used.
 */
class ReplayThrottlingPolicy
{
    using DynamicThrottleParams = vespalib::SharedOperationThrottler::DynamicThrottleParams;
    std::optional<DynamicThrottleParams> _params;

public:
    explicit ReplayThrottlingPolicy(std::optional<DynamicThrottleParams> params)
        : _params(std::move(params))
    {
    }
    const std::optional<DynamicThrottleParams>& get_params() const noexcept {  return _params; }
};

}
