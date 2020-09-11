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
                                                   const RpcTargetFactory& target_factory)
    : _slobrok_mirror(slobrok_mirror),
      _target_factory(target_factory),
      _targets_rwmutex()
{
}

CachingRpcTargetResolver::~CachingRpcTargetResolver() = default;

vespalib::string
CachingRpcTargetResolver::address_to_slobrok_id(const api::StorageMessageAddress& address) {
    vespalib::asciistream as;
    as << "storage/cluster." << address.getCluster()
       << '/' << ((address.getNodeType() == lib::NodeType::STORAGE) ? "storage" : "distributor")
       << '/' << address.getIndex();
    return as.str();
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::lookup_target(const vespalib::string& slobrok_id, uint32_t curr_slobrok_gen) {
    std::shared_lock lock(_targets_rwmutex);
    auto itr = _targets.find(slobrok_id);
    if ((itr != _targets.end())
        && itr->second->_target->is_valid()
        && (itr->second->_slobrok_gen == curr_slobrok_gen)) {
        return itr->second;
    }
    return {};
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::consider_update_target(const vespalib::string& slobrok_id,
                                                 const vespalib::string& connection_spec,
                                                 uint32_t curr_slobrok_gen,
                                                 const UniqueLock& targets_lock) {
    (void) targets_lock;
    // If address has the same spec as the existing target, just reuse it.
    auto itr = _targets.find(slobrok_id);
    if ((itr != _targets.end())
        && (itr->second->_target->is_valid())
        && (itr->second->_spec == connection_spec))
    {
        LOG(info, "Updating existing mapping '%s' -> '%s' (gen %u) to gen %u",
            slobrok_id.c_str(), connection_spec.c_str(), itr->second->_slobrok_gen, curr_slobrok_gen);
        itr->second->_slobrok_gen = curr_slobrok_gen;
        return itr->second;
    }
    return {};
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::insert_new_target_mapping(const vespalib::string& slobrok_id,
                                                    const vespalib::string& connection_spec,
                                                    uint32_t curr_slobrok_gen,
                                                    const UniqueLock& targets_lock) {
    (void) targets_lock;
    auto target = _target_factory.make_target(connection_spec, curr_slobrok_gen); // TODO expensive inside lock?
    assert(target);
    std::shared_ptr<RpcTarget> rpc_target(std::move(target));
    _targets[slobrok_id] = rpc_target;
    LOG(info, "Added mapping '%s' -> '%s' at gen %u", slobrok_id.c_str(), connection_spec.c_str(), curr_slobrok_gen);
    return rpc_target;
}

std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::resolve_rpc_target(const api::StorageMessageAddress& address) {
    // TODO or map directly from address to target instead of going via stringification? Needs hashing, if so.
    auto slobrok_id = address_to_slobrok_id(address);
    const uint32_t curr_slobrok_gen = _slobrok_mirror.updates();
    if (auto result = lookup_target(slobrok_id, curr_slobrok_gen)) {
        return result;
    }
    auto specs = _slobrok_mirror.lookup(slobrok_id); // FIXME string type mismatch; implicit conv!
    if (specs.empty()) {
        // TODO: Replace all info logging with debug logging.
        LOG(info, "Found no mapping for '%s'", slobrok_id.c_str());
        // TODO return potentially stale existing target if no longer existing in SB?
        // TODO or clear any existing mapping?
        return {};
    }
    // Note: We don't use wildcards so there is a 1-to-1 mapping between service name / slobrok id and connection spec.
    assert(specs.size() == 1);
    const auto& connection_spec = specs[0].second;
    std::unique_lock lock(_targets_rwmutex);
    if (auto result = consider_update_target(slobrok_id, connection_spec, curr_slobrok_gen, lock)) {
        return result;
    }
    return insert_new_target_mapping(slobrok_id, connection_spec, curr_slobrok_gen, lock);
}

}
