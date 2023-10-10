// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_target.h"
#include "rpc_target_pool.h"

namespace storage::rpc {

RpcTargetPool::RpcTargetPool(RpcTargetVector&& targets, const vespalib::string& spec, uint32_t slobrok_gen)
    : _targets(std::move(targets)),
      _spec(spec),
      _slobrok_gen(slobrok_gen)
{
}

std::shared_ptr<RpcTarget>
RpcTargetPool::get_target(uint64_t bucket_id) const
{
    return _targets[bucket_id % _targets.size()];
}

}
