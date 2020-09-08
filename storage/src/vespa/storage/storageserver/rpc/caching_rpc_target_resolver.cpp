// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "caching_rpc_target_resolver.h"
#include "shared_rpc_resources.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".storage.caching_rpc_target_resolver");

namespace storage::rpc {

CachingRpcTargetResolver::CachingRpcTargetResolver(SharedRpcResources& rpc_resources)
    : _rpc_resources(rpc_resources)
{
}

CachingRpcTargetResolver::~CachingRpcTargetResolver() = default;

namespace {

vespalib::string address_to_slobrok_id(const api::StorageMessageAddress& address) {
    vespalib::asciistream as;
    as << "storage/cluster." << address.getCluster()
       << '/' << ((address.getNodeType() == lib::NodeType::STORAGE) ? "storage" : "distributor")
       << '/' << address.getIndex();
    return as.str();
}

}

// TODO ensure this is robust and performant wrt. visitor clients constantly bumping
// slobrok generations by registering new sessions all the time.
std::shared_ptr<RpcTarget>
CachingRpcTargetResolver::resolve_rpc_target(const api::StorageMessageAddress& address) {
    // TODO or map directly from address to target instead of going via stringification? Needs hashing, if so.
    auto sb_id = address_to_slobrok_id(address);
    const uint32_t current_sb_gen = _rpc_resources.slobrok_mirror().updates();
    {
        std::shared_lock lock(_targets_rwmutex);
        auto target_iter = _targets.find(sb_id);
        if ((target_iter != _targets.end())
            && target_iter->second->_target->IsValid()
            && (target_iter->second->_sb_generation == current_sb_gen))
        {
            return target_iter->second;
        }
    }
    auto specs = _rpc_resources.slobrok_mirror().lookup(sb_id); // FIXME string type mismatch; implicit conv!
    if (specs.empty()) {
        LOG(info, "Found no mapping for %s", sb_id.c_str());
        // TODO return potentially stale existing target if no longer existing in SB?
        // TODO or clear any existing mapping?
        return {};
    }
    const auto& candidate_spec = specs[0].second; // Always use first spec in list. TODO correct?
    std::unique_lock lock(_targets_rwmutex);
    // If address has the same spec as the existing target, just reuse it.
    auto target_iter = _targets.find(sb_id);
    if ((target_iter != _targets.end())
        && (target_iter->second->_target->IsValid())
        && (target_iter->second->_spec == candidate_spec))
    {
        LOG(info, "Updating existing mapping %s -> %s (gen %u) to gen %u",
            sb_id.c_str(), candidate_spec.c_str(), target_iter->second->_sb_generation, current_sb_gen);
        target_iter->second->_sb_generation = current_sb_gen;
        return target_iter->second;
    }
    // Insert new mapping or update the old one.
    auto* raw_target = _rpc_resources.supervisor().GetTarget(candidate_spec.c_str()); // TODO expensive inside lock?
    assert(raw_target);
    auto rpc_target = std::make_shared<RpcTarget>(raw_target, candidate_spec, current_sb_gen);
    _targets[sb_id] = rpc_target;
    LOG(info, "Added mapping %s -> %s at gen %u", sb_id.c_str(), candidate_spec.c_str(), current_sb_gen);
    return rpc_target;
}


}
