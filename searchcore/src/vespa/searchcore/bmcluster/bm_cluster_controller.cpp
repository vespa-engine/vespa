// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster_controller.h"
#include "bm_cluster.h"
#include "bm_node.h"
#include "i_bm_distribution.h"
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

namespace search::bmcluster {

namespace {

FRT_RPCRequest *
make_set_cluster_state_request(const IBmDistribution& distribution)
{
    storage::lib::ClusterStateBundle bundle(distribution.get_cluster_state_bundle());
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

BmClusterController::BmClusterController(BmCluster& cluster, const IBmDistribution &distribution)
    : _cluster(cluster),
      _distribution(distribution)
{
}

void
BmClusterController::propagate_cluster_state(uint32_t node_idx, bool distributor)
{
    static vespalib::string _storage("storage");
    StorageMessageAddress storage_address(&_storage, distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE, node_idx);
    auto req = make_set_cluster_state_request(_distribution);
    auto& shared_rpc_resources = _cluster.get_rpc_client();
    auto target_resolver = std::make_unique<storage::rpc::CachingRpcTargetResolver>(shared_rpc_resources.slobrok_mirror(),
                                                                                    shared_rpc_resources.target_factory(), 1);
    uint64_t fake_bucket_id = 0;
    auto target = target_resolver->resolve_rpc_target(storage_address, fake_bucket_id);
    target->get()->InvokeSync(req, 10.0); // 10 seconds timeout
    assert(!req->IsError());
    req->internal_subref();
}

void
BmClusterController::propagate_cluster_state(bool distributor)
{
    uint32_t num_nodes = _cluster.get_num_nodes();
    for (uint32_t node_idx = 0; node_idx < num_nodes; ++node_idx) {
        auto node = _cluster.get_node(node_idx);
        if (node && node->has_storage_layer(distributor)) {
            propagate_cluster_state(node_idx, distributor);
        }
    }
}

void
BmClusterController::propagate_cluster_state()
{
    propagate_cluster_state(false);
    propagate_cluster_state(true);
}

}
