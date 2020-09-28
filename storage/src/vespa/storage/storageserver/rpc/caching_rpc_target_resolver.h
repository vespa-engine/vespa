// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target.h"
#include "rpc_target_factory.h"
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>
#include <shared_mutex>

namespace slobrok::api { class IMirrorAPI; }

namespace storage::rpc {

/**
 * Class that resolves and caches rpc targets based on StorageMessageAddress that is mapped to slobrok id,
 * with lookup in a slobrok mirror.
 */
class CachingRpcTargetResolver {

    struct AddressInternalHasher {
        size_t operator()(const api::StorageMessageAddress& addr) const noexcept {
            return addr.internal_storage_hash();
        }
    };
    using TargetHashMap = vespalib::hash_map<api::StorageMessageAddress,
                                             std::shared_ptr<RpcTarget>,
                                             AddressInternalHasher>;
    using UniqueLock = std::unique_lock<std::shared_mutex>;

    const slobrok::api::IMirrorAPI& _slobrok_mirror;
    const RpcTargetFactory&         _target_factory;
    mutable std::shared_mutex       _targets_rwmutex;
    TargetHashMap                   _targets; // TODO LRU? Size cap?

    std::shared_ptr<RpcTarget> lookup_target(const api::StorageMessageAddress& address,
                                             uint32_t curr_slobrok_gen);
    std::shared_ptr<RpcTarget> consider_update_target(const api::StorageMessageAddress& address,
                                                      const vespalib::string& connection_spec,
                                                      uint32_t curr_slobrok_gen,
                                                      const UniqueLock& targets_lock);

    std::shared_ptr<RpcTarget> insert_new_target_mapping(const api::StorageMessageAddress& address,
                                                         const vespalib::string& connection_spec,
                                                         uint32_t curr_slobrok_gen,
                                                         const UniqueLock& targets_lock);

public:
    CachingRpcTargetResolver(const slobrok::api::IMirrorAPI& slobrok_mirror,
                             const RpcTargetFactory& target_factory);
    ~CachingRpcTargetResolver();

    static vespalib::string address_to_slobrok_id(const api::StorageMessageAddress& address);

    std::shared_ptr<RpcTarget> resolve_rpc_target(const api::StorageMessageAddress& address);
};

} // storage::rpc
