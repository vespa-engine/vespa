// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "cluster_controller_api_rpc_service.h"
#include "shared_rpc_resources.h"
#include "slime_cluster_state_bundle_codec.h"
#include <vespa/storage/storageserver/communicationmanager.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpcrequestwrapper.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.cluster_controller_api_rpc_service");

namespace storage::rpc {

ClusterControllerApiRpcService::ClusterControllerApiRpcService(
        MessageDispatcher& message_dispatcher,
        SharedRpcResources& rpc_resources)
    : _message_dispatcher(message_dispatcher),
      _closed(false)
{
    register_server_methods(rpc_resources);
}

ClusterControllerApiRpcService::~ClusterControllerApiRpcService() = default;

void ClusterControllerApiRpcService::close() {
    _closed.store(true);
}

void ClusterControllerApiRpcService::register_server_methods(SharedRpcResources& rpc_resources) {
    FRT_ReflectionBuilder rb(&rpc_resources.supervisor());

    rb.DefineMethod("getnodestate3", "sii", "ss", FRT_METHOD(ClusterControllerApiRpcService::RPC_getNodeState2), this);
    rb.MethodDesc("Get state of this node");
    rb.ParamDesc("nodestate", "Expected state of given node. If correct, the "
                              "request will be queued on target until it changes. To not give "
                              "any state use the string 'unknown', enforcing a direct reply.");
    rb.ParamDesc("timeout", "Timeout of message in milliseconds, set by the state requester");
    rb.ReturnDesc("nodestate", "State string for this node");
    rb.ReturnDesc("hostinfo", "Information about host this node is running on");
    //-------------------------------------------------------------------------
    rb.DefineMethod("getnodestate2", "si", "s", FRT_METHOD(ClusterControllerApiRpcService::RPC_getNodeState2), this);
    rb.MethodDesc("Get state of this node");
    rb.ParamDesc("nodestate", "Expected state of given node. If correct, the "
                              "request will be queued on target until it changes. To not give "
                              "any state use the string 'unknown', enforcing a direct reply.");
    rb.ParamDesc("timeout", "Timeout of message in milliseconds, set by the state requester");
    rb.ReturnDesc("nodestate", "State string for this node");
    //-------------------------------------------------------------------------
    rb.DefineMethod("setsystemstate2", "s", "", FRT_METHOD(ClusterControllerApiRpcService::RPC_setSystemState2), this);
    rb.MethodDesc("Set systemstate on this node");
    rb.ParamDesc("systemstate", "New systemstate to set");
    //-------------------------------------------------------------------------
    rb.DefineMethod("setdistributionstates", "bix", "", FRT_METHOD(ClusterControllerApiRpcService::RPC_setDistributionStates), this);
    rb.MethodDesc("Set distribution states for cluster and bucket spaces");
    rb.ParamDesc("compressionType", "Compression type for payload");
    rb.ParamDesc("uncompressedSize", "Uncompressed size for payload");
    rb.ParamDesc("payload", "Binary Slime format payload");
    //-------------------------------------------------------------------------
    rb.DefineMethod("activate_cluster_state_version", "i", "i", FRT_METHOD(ClusterControllerApiRpcService::RPC_activateClusterStateVersion), this);
    rb.MethodDesc("Explicitly activates an already prepared cluster state version");
    rb.ParamDesc("activate_version", "Expected cluster state version to activate");
    rb.ReturnDesc("actual_version", "Cluster state version that was prepared on the node prior to receiving RPC");
    //-------------------------------------------------------------------------
    rb.DefineMethod("getcurrenttime", "", "lis", FRT_METHOD(ClusterControllerApiRpcService::RPC_getCurrentTime), this);
    rb.MethodDesc("Get current time on this node");
    rb.ReturnDesc("seconds", "Current time in seconds since epoch");
    rb.ReturnDesc("nanoseconds", "additional nanoseconds since epoch");
    rb.ReturnDesc("hostname", "Host name");
}

// TODO remove? is this used by anyone?
void ClusterControllerApiRpcService::RPC_getCurrentTime(FRT_RPCRequest* req) {
    if (_closed) {
        LOG(debug, "Not handling RPC call getCurrentTime() as we have closed");
        req->SetError(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN, "Node shutting down");
        return;
    }
    //TODO Should we unify on std::chrono here too ?
    struct timespec t;
    clock_gettime(CLOCK_REALTIME, &t);
    req->GetReturn()->AddInt64(t.tv_sec);
    req->GetReturn()->AddInt32(t.tv_nsec);
    vespalib::string hostname = vespalib::HostName::get();
    req->GetReturn()->AddString(hostname.c_str());
    // all handled, will return immediately
}

void ClusterControllerApiRpcService::detach_and_forward_to_enqueuer(
        std::shared_ptr<api::StorageMessage> cmd,
        FRT_RPCRequest* req)
{
    // Create a request object to avoid needing a separate transport type
    cmd->setTransportContext(std::make_unique<StorageTransportContext>(std::make_unique<RPCRequestWrapper>(req)));
    req->Detach();
    _message_dispatcher.dispatch_async(std::move(cmd));
}

void ClusterControllerApiRpcService::RPC_getNodeState2(FRT_RPCRequest* req) {
    if (_closed) {
        LOG(debug, "Not handling RPC call getNodeState2() as we have closed");
        req->SetError(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN, "Node shutting down");
        return;
    }

    vespalib::string expected(req->GetParams()->GetValue(0)._string._str,
                              req->GetParams()->GetValue(0)._string._len);

    auto cmd = std::make_shared<api::GetNodeStateCommand>(expected != "unknown"
                                                          ? std::make_unique<lib::NodeState>(expected)
                                                          : std::unique_ptr<lib::NodeState>());

    cmd->setPriority(api::StorageMessage::VERYHIGH);
    cmd->setTimeout(std::chrono::milliseconds(req->GetParams()->GetValue(1)._intval32));
    if (req->GetParams()->GetNumValues() > 2) {
        cmd->setSourceIndex(req->GetParams()->GetValue(2)._intval32);
    }
    detach_and_forward_to_enqueuer(std::move(cmd), req);
}

void ClusterControllerApiRpcService::RPC_setSystemState2(FRT_RPCRequest* req) {
    if (_closed) {
        LOG(debug, "Not handling RPC call setSystemState2() as we have closed");
        req->SetError(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN, "Node shutting down");
        return;
    }
    vespalib::string systemStateStr(req->GetParams()->GetValue(0)._string._str,
                                    req->GetParams()->GetValue(0)._string._len);
    lib::ClusterState systemState(systemStateStr);

    auto cmd = std::make_shared<api::SetSystemStateCommand>(lib::ClusterStateBundle(systemState));
    cmd->setPriority(api::StorageMessage::VERYHIGH);

    detach_and_forward_to_enqueuer(std::move(cmd), req);
}

namespace {

std::shared_ptr<const lib::ClusterStateBundle> decode_bundle_from_params(const FRT_Values& params) {
    const uint32_t uncompressed_length = params[1]._intval32;
    if (uncompressed_length > ClusterControllerApiRpcService::StateBundleMaxUncompressedSize) {
        throw std::range_error(vespalib::make_string("RPC ClusterStateBundle uncompressed size (%u) is "
                                                     "greater than max size (%u)", uncompressed_length,
                                                     ClusterControllerApiRpcService::StateBundleMaxUncompressedSize));
    }
    SlimeClusterStateBundleCodec codec;
    EncodedClusterStateBundle encoded_bundle;
    encoded_bundle._compression_type = vespalib::compression::CompressionConfig::toType(params[0]._intval8);
    encoded_bundle._uncompressed_length = uncompressed_length;
    // Caution: type cast to const ptr is essential or DataBuffer behavior changes!
    encoded_bundle._buffer = std::make_unique<vespalib::DataBuffer>(
            static_cast<const char*>(params[2]._data._buf), params[2]._data._len);
    return codec.decode(encoded_bundle);
}

}

void ClusterControllerApiRpcService::RPC_setDistributionStates(FRT_RPCRequest* req) {
    if (_closed) {
        LOG(debug, "Not handling RPC call setDistributionStates() as we have closed");
        req->SetError(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN, "Node shutting down");
        return;
    }
    std::shared_ptr<const lib::ClusterStateBundle> state_bundle;
    try {
        state_bundle = decode_bundle_from_params(*req->GetParams());
    } catch (std::exception& e) {
        LOG(error, "setDistributionStates RPC failed decoding: %s", e.what());
        req->SetError(RPCRequestWrapper::ERR_BAD_REQUEST, e.what());
        return;
    }
    LOG(debug, "Got state bundle %s", state_bundle->toString().c_str());

    // TODO add constructor taking in shared_ptr directly instead?
    auto cmd = std::make_shared<api::SetSystemStateCommand>(*state_bundle);
    cmd->setPriority(api::StorageMessage::VERYHIGH);

    detach_and_forward_to_enqueuer(std::move(cmd), req);
}

void ClusterControllerApiRpcService::RPC_activateClusterStateVersion(FRT_RPCRequest* req) {
    if (_closed) {
        LOG(debug, "Not handling RPC call activate_cluster_state_version() as we have closed");
        req->SetError(RPCRequestWrapper::ERR_NODE_SHUTTING_DOWN, "Node shutting down");
        return;
    }

    const uint32_t activate_version = req->GetParams()->GetValue(0)._intval32;
    auto cmd = std::make_shared<api::ActivateClusterStateVersionCommand>(activate_version);
    cmd->setPriority(api::StorageMessage::VERYHIGH);

    LOG(debug, "Got state activation request for version %u", activate_version);

    detach_and_forward_to_enqueuer(std::move(cmd), req);
}

}
