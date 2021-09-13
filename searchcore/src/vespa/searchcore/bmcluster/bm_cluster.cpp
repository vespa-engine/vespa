// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster.h"
#include "bm_cluster_controller.h"
#include "bm_feed.h"
#include "bm_message_bus.h"
#include "bm_node.h"
#include "spi_bm_feed_handler.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.bm_cluster");

using cloud::config::SlobroksConfigBuilder;
using config::ConfigSet;
using messagebus::MessagebusConfigBuilder;
using storage::rpc::SharedRpcResources;

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
      _nodes(params.get_num_nodes())
    
{
    _message_bus_config->add_builders(*_config_set);
    _rpc_client_config->add_builders(*_config_set);
    vespalib::mkdir(_base_dir, false);
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
BmCluster::make_node(unsigned int node_idx)
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
    for (unsigned int node_idx = 0; node_idx < _nodes.size(); ++node_idx) {
        make_node(node_idx);
    }
}

BmNode&
BmCluster::get_node(unsigned int node_idx)
{
    assert(node_idx < _nodes.size());
    assert(_nodes[node_idx]);
    return *_nodes[node_idx];
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
    auto& node = get_node(0);
    for (unsigned int i = 0; i < feed.num_buckets(); ++i) {
        node.create_bucket(feed.make_bucket(i));
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
    BmClusterController fake_controller(get_rpc_client(), _params.get_num_nodes());
    unsigned int node_idx = 0;
    for (const auto &node : _nodes) {
        if (node) {
            fake_controller.set_cluster_up(node_idx, false);
        }
        ++node_idx;
    }
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
    BmClusterController fake_controller(get_rpc_client(), _params.get_num_nodes());
    unsigned int node_idx = 0;
    for (const auto &node : _nodes) {
        if (node) {
            fake_controller.set_cluster_up(node_idx, true);
        }
        ++node_idx;
    }
    // Wait for bucket ownership transfer safe time
    std::this_thread::sleep_for(2s);
}

void
BmCluster::create_feed_handlers()
{
    for (const auto &node : _nodes) {
        if (node) {
            node->create_feed_handler(_params);
        }
    }
}

void
BmCluster::shutdown_feed_handlers()
{
    for (const auto &node : _nodes) {
        if (node) {
            node->shutdown_feed_handler();
        }
    }
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
    create_feed_handlers();
}

void
BmCluster::stop()
{
    shutdown_feed_handlers();
    stop_message_bus();
    shutdown_distributors();
    shutdown_service_layers();
}

IBmFeedHandler*
BmCluster::get_feed_handler()
{
    return get_node(0).get_feed_handler();
}

}
