// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>

class FRT_Target;

namespace storage::rpc {

struct RpcTarget {
    FRT_Target*            _target;
    const vespalib::string _spec;
    uint32_t               _sb_generation;

    // Target must have ref count of at least 1
    RpcTarget(FRT_Target* target,
              vespalib::stringref spec,
              uint32_t sb_generation);
    RpcTarget(const RpcTarget&) = delete;
    RpcTarget& operator=(const RpcTarget&) = delete;
    RpcTarget(RpcTarget&&) = delete;
    RpcTarget& operator=(RpcTarget&&) = delete;
    ~RpcTarget();
};

}
