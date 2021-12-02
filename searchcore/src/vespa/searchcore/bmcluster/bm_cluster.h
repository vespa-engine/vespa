// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/config/documenttypes_config_fwd.h>
#include "bm_cluster_params.h"
#include <memory>
#include <vector>

namespace config {

class IConfigContext;
class ConfigSet;

}

namespace document {

class DocumentTypeRepo;
class FieldSetRepo;

}
namespace mbus { class Slobrok; }
namespace storage::rpc { class SharedRpcResources; }

namespace search::bmcluster {

class BmClusterController;
class BmDistribution;
class BmFeed;
class BmMessageBus;
class BmNode;
class BmNodeStats;
class BucketDbSnapshotVector;
class IBmDistribution;
class IBmFeedHandler;

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
    vespalib::string                                  _base_dir;
    int                                               _base_port;
    std::shared_ptr<DocumenttypesConfig>              _document_types;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    std::unique_ptr<const document::FieldSetRepo>     _field_set_repo;
    std::shared_ptr<BmDistribution>                   _real_distribution;
    std::shared_ptr<const IBmDistribution>            _distribution;
    std::vector<std::unique_ptr<BmNode>>              _nodes;
    std::shared_ptr<BmClusterController>              _cluster_controller;
    std::unique_ptr<IBmFeedHandler>                   _feed_handler;

public:
    BmCluster(const vespalib::string& base_dir, int base_port, const BmClusterParams& params, std::shared_ptr<DocumenttypesConfig> document_types, std::shared_ptr<const document::DocumentTypeRepo> repo);
    ~BmCluster();
    void start_slobrok();
    void stop_slobrok();
    void wait_slobrok(const vespalib::string &name);
    void start_message_bus();
    void stop_message_bus();
    void start_rpc_client();
    void stop_rpc_client();
    void start_service_layers();
    void start_distributors();
    void create_feed_handler();
    void shutdown_feed_handler();
    void shutdown_distributors();
    void shutdown_service_layers();
    void create_buckets(BmFeed &feed);
    void initialize_providers();
    void start(BmFeed &feed);
    void stop();
    const storage::rpc::SharedRpcResources &get_rpc_client() const { return *_rpc_client; }
    storage::rpc::SharedRpcResources &get_rpc_client() { return *_rpc_client; }
    BmMessageBus& get_message_bus() { return *_message_bus; }
    const IBmDistribution& get_distribution() { return *_distribution; }
    void make_node(uint32_t node_idx);
    void make_nodes();
    IBmFeedHandler* get_feed_handler();
    uint32_t get_num_nodes() const { return _nodes.size(); }
    BmNode *get_node(uint32_t node_idx) const { return node_idx < _nodes.size() ? _nodes[node_idx].get() : nullptr; }
    std::vector<BmNodeStats> get_node_stats();
    BmDistribution& get_real_distribution() { return *_real_distribution; }
    void propagate_cluster_state();
    BucketDbSnapshotVector get_bucket_db_snapshots();
};

}
