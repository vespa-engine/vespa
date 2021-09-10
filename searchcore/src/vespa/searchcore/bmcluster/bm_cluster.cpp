// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_cluster.h"
#include "bm_message_bus.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
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

BmCluster::BmCluster(const BmClusterParams& params, std::shared_ptr<const document::DocumentTypeRepo> repo)
    : _params(params),
      _slobrok_port(9018),
      _rpc_client_port(9019),
      _message_bus_config(std::make_unique<MessageBusConfigSet>(message_bus_config_id, _slobrok_port)),
      _rpc_client_config(std::make_unique<RpcClientConfigSet>(rpc_client_config_id, _slobrok_port)),
      _config_set(std::make_unique<config::ConfigSet>()),
      _config_context(std::make_shared<config::ConfigContext>(*_config_set)),
      _slobrok(),
      _message_bus(),
      _rpc_client(),
      _repo(repo)
    
{
    _message_bus_config->add_builders(*_config_set);
    _rpc_client_config->add_builders(*_config_set);
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

}
