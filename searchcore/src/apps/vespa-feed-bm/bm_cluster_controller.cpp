// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster_controller.h"
#include <vespa/storage/storageserver/rpc/caching_rpc_target_resolver.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/slime_cluster_state_bundle_codec.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/slobrok/sbmirror.h>

using storage::api::StorageMessageAddress;
using storage::rpc::SharedRpcResources;
using storage::lib::NodeType;

namespace feedbm {

namespace {

FRT_RPCRequest *
make_set_cluster_state_request()
{
    storage::lib::ClusterStateBundle bundle(storage::lib::ClusterState("version:2 distributor:1 storage:1"));
    storage::rpc::SlimeClusterStateBundleCodec codec;
    auto encoded_bundle = codec.encode(bundle);
    auto *req = new FRT_RPCRequest();
    auto* params = req->GetParams();
    params->AddInt8(static_cast<uint8_t>(encoded_bundle._compression_type));
    params->AddInt32(encoded_bundle._uncompressed_length);
    params->AddData(std::move(*encoded_bundle._buffer));
    req->SetMethodName("setdistributionstates");
    return req;
}

}

BmClusterController::BmClusterController(SharedRpcResources& shared_rpc_resources_in)
    : _shared_rpc_resources(shared_rpc_resources_in)
{
}

void
BmClusterController::set_cluster_up(bool distributor)
{
    static vespalib::string _storage("storage");
    StorageMessageAddress storage_address(&_storage, distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE, 0);
    auto req = make_set_cluster_state_request();
    auto target_resolver = std::make_unique<storage::rpc::CachingRpcTargetResolver>(_shared_rpc_resources.slobrok_mirror(),
                                                                                    _shared_rpc_resources.target_factory(), 1);
    uint64_t fake_bucket_id = 0;
    auto target = target_resolver->resolve_rpc_target(storage_address, fake_bucket_id);
    target->get()->InvokeSync(req, 10.0); // 10 seconds timeout
    assert(!req->IsError());
    req->SubRef();
}

}
