// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>

class FRT_Target;

namespace storage::rpc {

/**
 * Simple wrapper API to access a FRT_Target.
 */
class WrappedFrtTarget {
public:
    virtual ~WrappedFrtTarget() = default;
    virtual FRT_Target* get() = 0;
    virtual bool is_valid() const = 0;
};

struct RpcTarget {
    std::unique_ptr<WrappedFrtTarget> _target;
    const vespalib::string _spec;
    uint32_t               _slobrok_gen;

    RpcTarget(std::unique_ptr<WrappedFrtTarget> target,
              vespalib::stringref spec,
              uint32_t slobrok_gen);
    RpcTarget(const RpcTarget&) = delete;
    RpcTarget& operator=(const RpcTarget&) = delete;
    RpcTarget(RpcTarget&&) = delete;
    RpcTarget& operator=(RpcTarget&&) = delete;
    ~RpcTarget();
};

}
