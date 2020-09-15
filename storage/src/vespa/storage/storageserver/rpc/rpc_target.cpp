// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpc_target.h"

namespace storage::rpc {

RpcTarget::RpcTarget(std::unique_ptr<WrappedFrtTarget> target, vespalib::stringref spec, uint32_t slobrok_gen)
    : _target(std::move(target)),
      _spec(spec),
      _slobrok_gen(slobrok_gen)
{}

RpcTarget::~RpcTarget() = default;

}
