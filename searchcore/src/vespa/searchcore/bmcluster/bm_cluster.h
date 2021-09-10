// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "bm_cluster_params.h"

namespace config {

class IConfigContext;
class ConfigSet;

}

namespace document { class DocumentTypeRepo; }
namespace mbus { class Slobrok; }
namespace storage::rpc { class SharedRpcResources; }

namespace search::bmcluster {

class BmMessageBus;

/*
 * Class representing a benchmark cluster with one or more benchmark nodes.
 */
class BmCluster {
    struct MessageBusConfigSet;
    struct RpcClientConfigSet;
    BmClusterParams                                   _params;
    int                                               _slobrok_port;
    int                                               _rpc_client_port;
    std::unique_ptr<MessageBusConfigSet>              _message_bus_config;
    std::unique_ptr<RpcClientConfigSet>               _rpc_client_config;
    std::unique_ptr<config::ConfigSet>                _config_set;
    std::shared_ptr<config::IConfigContext>           _config_context;
    std::unique_ptr<mbus::Slobrok>                    _slobrok;
    std::unique_ptr<BmMessageBus>                     _message_bus;
    std::unique_ptr<storage::rpc::SharedRpcResources> _rpc_client;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;

public:
    BmCluster(const BmClusterParams& params, std::shared_ptr<const document::DocumentTypeRepo> repo);
    ~BmCluster();
    void start_slobrok();
    void stop_slobrok();
    void wait_slobrok(const vespalib::string &name);
    void start_message_bus();
    void stop_message_bus();
    void start_rpc_client();
    void stop_rpc_client();
    storage::rpc::SharedRpcResources &get_rpc_client() { return *_rpc_client; }
    BmMessageBus& get_message_bus() { return *_message_bus; }
};

}
