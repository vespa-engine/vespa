// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target.h"
#include "rpc_target_factory.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>
#include <shared_mutex>

namespace slobrok::api { class IMirrorAPI; }

namespace storage {

namespace api { class StorageMessageAddress; }

namespace rpc {

/**
 * Class that resolves and caches rpc targets based on StorageMessageAddress that is mapped to slobrok id,
 * with lookup in a slobrok mirror.
 */
class CachingRpcTargetResolver {
private:
    const slobrok::api::IMirrorAPI& _slobrok_mirror;
    const RpcTargetFactory& _target_factory;
    using UniqueLock = std::unique_lock<std::shared_mutex>;
    mutable std::shared_mutex _targets_rwmutex;
    // TODO LRU? Size cap?
    vespalib::hash_map<vespalib::string, std::shared_ptr<RpcTarget>> _targets;

    std::shared_ptr<RpcTarget> lookup_target(const vespalib::string& slobrok_id, uint32_t curr_slobrok_gen);
    std::shared_ptr<RpcTarget> consider_update_target(const vespalib::string& slobrok_id,
                                                      const vespalib::string& connection_spec,
                                                      uint32_t curr_slobrok_gen,
                                                      const UniqueLock& targets_lock);

    std::shared_ptr<RpcTarget> insert_new_target_mapping(const vespalib::string& slobrok_id,
                                                         const vespalib::string& connection_spec,
                                                         uint32_t curr_slobrok_gen,
                                                         const UniqueLock& targets_lock);

public:
    explicit CachingRpcTargetResolver(const slobrok::api::IMirrorAPI& slobrok_mirror,
                                      const RpcTargetFactory& target_factory);
    ~CachingRpcTargetResolver();

    static vespalib::string address_to_slobrok_id(const api::StorageMessageAddress& address);

    std::shared_ptr<RpcTarget> resolve_rpc_target(const api::StorageMessageAddress& address);
};

} // rpc
} // storage
