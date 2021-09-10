// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster.h"
#include "bm_node.h"
#include "bm_message_bus.h"
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
      _repo(std::move(repo))
    
{
    _message_bus_config->add_builders(*_config_set);
    _rpc_client_config->add_builders(*_config_set);
    vespalib::mkdir(_base_dir, false);
}
 
BmCluster::~BmCluster()
{
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

std::unique_ptr<BmNode>
BmCluster::make_bm_node(int node_idx)
{
    vespalib::asciistream s;
    s << _base_dir << "/n" << node_idx;
    vespalib::string node_base_dir(s.str());
    int node_base_port = port_number(_base_port, PortBias::NUM_PORTS) + BmNode::num_ports() * node_idx;
    return BmNode::create(node_base_dir, node_base_port, node_idx, _params, _document_types, _slobrok_port);
}

}
