// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "caching_rpc_target_resolver.h"
#include "shared_rpc_resources.h"
#include <vespa/fnet/frt/target.h>
#include <vespa/slobrok/imirrorapi.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".storage.caching_rpc_target_resolver");

namespace storage::rpc {

CachingRpcTargetResolver::CachingRpcTargetResolver(const slobrok::api::IMirrorAPI& slobrok_mirror,
                                                   const RpcTargetFactory& target_factory,
                                                   size_t num_targets_per_node)
    : _slobrok_mirror(slobrok_mirror),
      _target_factory(target_factory),
      _targets_rwmutex(),
      _num_targets_per_node(num_targets_per_node)
{
}

CachingRpcTargetResolver::~CachingRpcTargetResolver() = default;

vespalib::string
CachingRpcTargetResolver::address_to_slobrok_id(const api::StorageMessageAddress& address) {
    vespalib::asciistream as;
    as << "storage/cluster." << address.getCluster()
       << '/' << ((address.getNodeType() == lib::NodeType::Type::STORAGE) ? "storage" : "distributor")
       << '/' << address.getIndex();
    return as.str();
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::lookup_target(const api::StorageMessageAddress& address, uint64_t bucket_id, uint32_t curr_slobrok_gen) {
    std::shared_lock lock(_targets_rwmutex);
    auto itr = _targets.find(address);
    if (itr != _targets.end()) {
        const auto& pool = itr->second;
        auto target = pool->get_target(bucket_id);
        if (target->is_valid() && (pool->slobrok_gen() == curr_slobrok_gen)) {
            return target;
        }
    }
    return {};
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::consider_update_target_pool(const api::StorageMessageAddress& address,
                                                      uint64_t bucket_id,
                                                      const vespalib::string& connection_spec,
                                                      uint32_t curr_slobrok_gen,
                                                      [[maybe_unused]] const UniqueLock& targets_lock) {
    // If address has the same spec as the existing target pool, just reuse it.
    auto itr = _targets.find(address);
    if (itr != _targets.end()) {
        auto& pool = itr->second;
        auto target = pool->get_target(bucket_id);
        if (target->is_valid() && (pool->spec() == connection_spec)) {
            LOG(debug, "Updating existing mapping '%s' -> '%s' (gen %u) to gen %u",
                address.toString().c_str(), connection_spec.c_str(),
                pool->slobrok_gen(), curr_slobrok_gen);
            pool->update_slobrok_gen(curr_slobrok_gen);
            return target;
        }
    }
    return {};
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::insert_new_target_mapping(const api::StorageMessageAddress& address,
                                                    uint64_t bucket_id,
                                                    const vespalib::string& connection_spec,
                                                    uint32_t curr_slobrok_gen,
                                                    [[maybe_unused]] const UniqueLock& targets_lock) {
    RpcTargetPool::RpcTargetVector targets;
    targets.reserve(_num_targets_per_node);
    for (size_t i = 0; i < _num_targets_per_node; ++i) {
        auto target = _target_factory.make_target(connection_spec); // TODO expensive inside lock?
        assert(target);
        targets.push_back(std::shared_ptr<RpcTarget>(std::move(target)));
    }
    // TODO emplacement (with replace) semantics to avoid need for default constructed K/V
    auto pool = std::make_shared<RpcTargetPool>(std::move(targets), connection_spec, curr_slobrok_gen);
    _targets[address] = pool;
    LOG(debug, "Added mapping '%s' -> '%s' at gen %u", address.toString().c_str(),
        connection_spec.c_str(), curr_slobrok_gen);
    return pool->get_target(bucket_id);
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::resolve_rpc_target(const api::StorageMessageAddress& address,
                                             uint64_t bucket_id) {
    const uint32_t curr_slobrok_gen = _slobrok_mirror.updates();
    if (auto result = lookup_target(address, bucket_id, curr_slobrok_gen)) {
        return result;
    }
    auto slobrok_id = address_to_slobrok_id(address);
    auto specs = _slobrok_mirror.lookup(slobrok_id); // FIXME string type mismatch; implicit conv!
    if (specs.empty()) {
        LOG(debug, "Found no mapping for '%s'", slobrok_id.c_str());
        // TODO return potentially stale existing target if no longer existing in SB?
        // TODO or clear any existing mapping?
        return {};
    }
    // Note: We don't use wildcards so there is a 1-to-1 mapping between service name / slobrok id and connection spec.
    assert(specs.size() == 1);
    const auto& connection_spec = specs[0].second;
    std::unique_lock lock(_targets_rwmutex);
    if (auto result = consider_update_target_pool(address, bucket_id, connection_spec, curr_slobrok_gen, lock)) {
        return result;
    }
    return insert_new_target_mapping(address, bucket_id, connection_spec, curr_slobrok_gen, lock);
}

std::shared_ptr<RpcTargetPool>
CachingRpcTargetResolver::resolve_rpc_target_pool(const api::StorageMessageAddress& address) {
    std::shared_lock lock(_targets_rwmutex);
    auto itr = _targets.find(address);
    if (itr != _targets.end()) {
        return itr->second;
    }
    return {};
}

}
