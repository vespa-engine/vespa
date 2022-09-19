// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster.h"
#include "bm_cluster_controller.h"
#include "bm_distribution.h"
#include "bm_feed.h"
#include "bm_message_bus.h"
#include "bm_node.h"
#include "bm_node_stats.h"
#include "bucket_db_snapshot_vector.h"
#include "document_api_message_bus_bm_feed_handler.h"
#include "spi_bm_feed_handler.h"
#include "storage_api_chain_bm_feed_handler.h"
#include "storage_api_message_bus_bm_feed_handler.h"
#include "storage_api_rpc_bm_feed_handler.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <filesystem>
#include <thread>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.bm_cluster");

using cloud::config::SlobroksConfigBuilder;
using config::ConfigSet;
using messagebus::MessagebusConfigBuilder;
using storage::rpc::SharedRpcResources;
using storage::rpc::StorageApiRpcService;
using storage::spi::PersistenceProvider;
using vespalib::compression::CompressionConfig;

namespace search::bmcluster {

namespace {

vespalib::string message_bus_config_id("bm-message-bus");
vespalib::string rpc_client_config_id("bm-rpc-client");

enum class PortBias
{
    SLOBROK_PORT = 0,
    RPC_CLIENT_PORT = 1,
    NUM_PORTS = 2        
};

int port_number(int base_port, PortBias bias)
{
    return base_port + static_cast<int>(bias);
}

void
make_slobroks_config(SlobroksConfigBuilder& slobroks, int slobrok_port)
{
    SlobroksConfigBuilder::Slobrok slobrok;
    slobrok.connectionspec = vespalib::make_string("tcp/localhost:%d", slobrok_port);
    slobroks.slobrok.push_back(std::move(slobrok));
}

std::vector<std::shared_ptr<BmStorageLinkContext>>
collect_storage_link_contexts(const std::vector<std::unique_ptr<BmNode>> &nodes, bool distributor)
{
    std::vector<std::shared_ptr<BmStorageLinkContext>> contexts;
    for (auto& node : nodes) {
        if (node) {
            contexts.emplace_back(node->get_storage_link_context(distributor));
        } else {
            contexts.emplace_back();
        }
    }
    return contexts;
}

std::vector<PersistenceProvider *>
collect_persistence_providers(const std::vector<std::unique_ptr<BmNode>> &nodes)
{
    std::vector<PersistenceProvider *> providers;
    for (auto& node : nodes) {
        if (node) {
            providers.emplace_back(node->get_persistence_provider());
        } else {
            providers.emplace_back(nullptr);
        }
    }
    return providers;
}

}

struct BmCluster::MessageBusConfigSet {
    vespalib::string              config_id;
    SlobroksConfigBuilder         slobroks;
    MessagebusConfigBuilder       messagebus;

    MessageBusConfigSet(const vespalib::string &config_id_in, int slobrok_port)
        : config_id(config_id_in),
          slobroks(),
          messagebus()
    {
        make_slobroks_config(slobroks, slobrok_port);
    }
    ~MessageBusConfigSet();

    void add_builders(ConfigSet &set) {
        set.addBuilder(config_id, &slobroks);
        set.addBuilder(config_id, &messagebus);
    }
};

BmCluster::MessageBusConfigSet::~MessageBusConfigSet() = default;

struct BmCluster::RpcClientConfigSet {
    vespalib::string      config_id;
    SlobroksConfigBuilder slobroks;

    RpcClientConfigSet(const vespalib::string &config_id_in, int slobrok_port)
        : config_id(config_id_in),
          slobroks()
    {
        make_slobroks_config(slobroks, slobrok_port);
    }
    ~RpcClientConfigSet();

    void add_builders(ConfigSet &set) {
        set.addBuilder(config_id, &slobroks);
    }
};

BmCluster::RpcClientConfigSet::~RpcClientConfigSet() = default;

BmCluster::BmCluster(const vespalib::string& base_dir, int base_port, const BmClusterParams& params, std::shared_ptr<DocumenttypesConfig> document_types, std::shared_ptr<const document::DocumentTypeRepo> repo)
    : _params(params),
      _slobrok_port(port_number(base_port, PortBias::SLOBROK_PORT)),
      _rpc_client_port(port_number(base_port, PortBias::RPC_CLIENT_PORT)),
      _message_bus_config(std::make_unique<MessageBusConfigSet>(message_bus_config_id, _slobrok_port)),
      _rpc_client_config(std::make_unique<RpcClientConfigSet>(rpc_client_config_id, _slobrok_port)),
      _config_set(std::make_unique<config::ConfigSet>()),
      _config_context(std::make_shared<config::ConfigContext>(*_config_set)),
      _slobrok(),
      _message_bus(),
      _rpc_client(),
      _base_dir(base_dir),
      _base_port(base_port),
      _document_types(std::move(document_types)),
      _repo(std::move(repo)),
      _field_set_repo(std::make_unique<const document::FieldSetRepo>(*_repo)),
      _real_distribution(std::make_shared<BmDistribution>(params.get_groups(), params.get_nodes_per_group(), params.get_redundancy())),
      _distribution(_real_distribution),
      _nodes(params.get_num_nodes()),
      _cluster_controller(std::make_shared<BmClusterController>(*this, *_distribution)),
      _feed_handler()
{
    _message_bus_config->add_builders(*_config_set);
    _rpc_client_config->add_builders(*_config_set);
    std::filesystem::create_directory(std::filesystem::path(_base_dir));
}
 
BmCluster::~BmCluster()
{
    _nodes.clear();
    stop_message_bus();
    stop_rpc_client();
    stop_slobrok();
}


void
BmCluster::start_slobrok()
{
    if (!_slobrok) {
        LOG(info, "start slobrok");
        _slobrok = std::make_unique<mbus::Slobrok>(_slobrok_port);
    }
}

void
BmCluster::stop_slobrok()
{
    if (_slobrok) {
        LOG(info, "stop slobrok");
        _slobrok.reset();
    }
}

void
BmCluster::wait_slobrok(const vespalib::string &name)
{
    auto &mirror = _rpc_client->slobrok_mirror();
    LOG(info, "Waiting for %s in slobrok", name.c_str());
    for (;;) {
        auto specs = mirror.lookup(name);
        if (!specs.empty()) {
            LOG(info, "Found %s in slobrok", name.c_str());
            return;
        }
        std::this_thread::sleep_for(100ms);
    }
}

void
BmCluster::start_message_bus()
{
    if (!_message_bus) {
        LOG(info, "Starting message bus");
        config::ConfigUri config_uri(message_bus_config_id, _config_context);
        _message_bus = std::make_unique<BmMessageBus>(config_uri, _repo);
        LOG(info, "Started message bus");
    }
}

void
BmCluster::stop_message_bus()
{
    if (_message_bus) {
        LOG(info, "stop message bus");
        _message_bus.reset();
    }
}

void
BmCluster::start_rpc_client()
{
    if (!_rpc_client) {
        LOG(info, "start rpc client");
        config::ConfigUri client_config_uri(rpc_client_config_id, _config_context);
        _rpc_client = std::make_unique<SharedRpcResources>
                      (client_config_uri, _rpc_client_port, 100, _params.get_rpc_events_before_wakeup());
        _rpc_client->start_server_and_register_slobrok(rpc_client_config_id);
    }
}

void
BmCluster::stop_rpc_client()
{
    if (_rpc_client) {
        LOG(info, "stop rpc client");
        _rpc_client->shutdown();
        _rpc_client.reset();
    }
}

void
BmCluster::make_node(uint32_t node_idx)
{
    assert(node_idx < _nodes.size());
    assert(!_nodes[node_idx]);
    vespalib::asciistream s;
    s << _base_dir << "/n" << node_idx;
    vespalib::string node_base_dir(s.str());
    int node_base_port = port_number(_base_port, PortBias::NUM_PORTS) + BmNode::num_ports() * node_idx;
    _nodes[node_idx] = BmNode::create(node_base_dir, node_base_port, node_idx, *this, _params, _document_types, _slobrok_port);
}

void
BmCluster::make_nodes()
{
    for (uint32_t node_idx = 0; node_idx < _nodes.size(); ++node_idx) {
        make_node(node_idx);
    }
}

void
BmCluster::initialize_providers()
{
    LOG(info, "start initialize");
    for (const auto &node : _nodes) {
        if (node) {
            node->initialize_persistence_provider();
        }
    }
}

void
BmCluster::create_buckets(BmFeed& feed)
{
    LOG(info, "create %u buckets", feed.num_buckets());
    for (unsigned int i = 0; i < feed.num_buckets(); ++i) {
        auto bucket = feed.make_bucket(i);
        uint32_t node_idx = _distribution->get_service_layer_node_idx(bucket);
        if (node_idx < _nodes.size()) {
            auto& node = _nodes[node_idx];
            if (node) {
                node->create_bucket(feed.make_bucket(i));
            }
        }
    }
}

void
BmCluster::start_service_layers()
{
    start_slobrok();
    for (const auto &node : _nodes) {
        if (node) {
            node->start_service_layer(_params);
        }
    }
    for (const auto &node : _nodes) {
        if (node) {
            node->wait_service_layer();
        }
    }
    start_rpc_client();
    for (const auto &node : _nodes) {
        if (node) {
            node->wait_service_layer_slobrok();
        }
    }
    _cluster_controller->propagate_cluster_state(false);
}

void
BmCluster::start_distributors()
{
    for (const auto &node : _nodes) {
        if (node) {
            node->start_distributor(_params);
        }
    }
    for (const auto &node : _nodes) {
        if (node) {
            node->wait_distributor_slobrok();
        }
    }
    _cluster_controller->propagate_cluster_state(true);
    // Wait for bucket ownership transfer safe time
    std::this_thread::sleep_for(2s);
}

void
BmCluster::create_feed_handler()
{
    StorageApiRpcService::Params rpc_params;
    // This is the same compression config as the default in stor-communicationmanager.def.
    rpc_params.compression_config = CompressionConfig(CompressionConfig::Type::LZ4, 3, 90, 1024);
    rpc_params.num_rpc_targets_per_node = _params.get_rpc_targets_per_node();
    if (_params.get_use_document_api()) {
        _feed_handler = std::make_unique<DocumentApiMessageBusBmFeedHandler>(get_message_bus(), *_distribution);
    } else if (_params.get_enable_distributor()) {
        if (_params.get_use_storage_chain()) {
            auto contexts = collect_storage_link_contexts(_nodes, true);
            _feed_handler = std::make_unique<StorageApiChainBmFeedHandler>(std::move(contexts), *_distribution, true);
        } else if (_params.get_use_message_bus()) {
            _feed_handler = std::make_unique<StorageApiMessageBusBmFeedHandler>(get_message_bus(), *_distribution, true);
        } else {
            _feed_handler = std::make_unique<StorageApiRpcBmFeedHandler>(get_rpc_client(), _repo, rpc_params, *_distribution, true);
        }
    } else if (_params.needs_service_layer()) {
        if (_params.get_use_storage_chain()) {
            auto contexts = collect_storage_link_contexts(_nodes, false);
            _feed_handler = std::make_unique<StorageApiChainBmFeedHandler>(std::move(contexts), *_distribution, false);
        } else if (_params.get_use_message_bus()) {
            _feed_handler = std::make_unique<StorageApiMessageBusBmFeedHandler>(get_message_bus(), *_distribution, false);
        } else {
            _feed_handler = std::make_unique<StorageApiRpcBmFeedHandler>(get_rpc_client(), _repo, rpc_params, *_distribution, false);
        }
    } else {
        auto providers = collect_persistence_providers(_nodes);
        _feed_handler = std::make_unique<SpiBmFeedHandler>(std::move(providers), *_field_set_repo, *_distribution, _params.get_skip_get_spi_bucket_info());
    }
}

void
BmCluster::shutdown_feed_handler()
{
    _feed_handler.reset();
}

void
BmCluster::shutdown_distributors()
{
    for (const auto &node : _nodes) {
        if (node) {
            node->shutdown_distributor();
        }
    }
}

void
BmCluster::shutdown_service_layers()
{
    stop_rpc_client();
    for (const auto &node : _nodes) {
        if (node) {
            node->shutdown_service_layer();
        }
    }
    stop_slobrok();
}

void
BmCluster::start(BmFeed& feed)
{
    initialize_providers();
    if (!_params.needs_distributor()) {
        create_buckets(feed);
    }
    if (_params.needs_service_layer()) {
        start_service_layers();
    }
    if (_params.needs_distributor()) {
        start_distributors();
    }
    if (_params.needs_message_bus()) {
        start_message_bus();
    }
    create_feed_handler();
}

void
BmCluster::stop()
{
    shutdown_feed_handler();
    stop_message_bus();
    shutdown_distributors();
    shutdown_service_layers();
}

IBmFeedHandler*
BmCluster::get_feed_handler()
{
    return _feed_handler.get();
}

std::vector<BmNodeStats>
BmCluster::get_node_stats()
{
    std::vector<BmNodeStats> node_stats(_nodes.size());
    storage::lib::ClusterState baseline_state(*_distribution->get_cluster_state_bundle().getBaselineClusterState());
    for (const auto &node : _nodes) {
        if (node) {
            node->merge_node_stats(node_stats, baseline_state);
        }
    }
    return node_stats;
}

void
BmCluster::propagate_cluster_state()
{
    _cluster_controller->propagate_cluster_state();
}

BucketDbSnapshotVector
BmCluster::get_bucket_db_snapshots()
{
    auto providers = collect_persistence_providers(_nodes);
    return BucketDbSnapshotVector(providers, _distribution->get_cluster_state_bundle());
}

}
