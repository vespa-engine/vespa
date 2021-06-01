// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "env.h"
#include "check-completion-handler.h"
#include <vespa/log/log.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <thread>
#include <chrono>

LOG_SETUP(".env");

using namespace std::chrono_literals;

namespace config::sentinel {

constexpr std::chrono::milliseconds CONFIG_TIMEOUT_MS = 3min;
constexpr std::chrono::milliseconds MODEL_TIMEOUT_MS = 1500ms;

Env::Env()
  : _cfgOwner(),
    _rpcCommandQueue(),
    _rpcServer(),
    _stateApi(),
    _startMetrics(),
    _stateServer(),
    _statePort(0)
{
    _startMetrics.startedTime = vespalib::steady_clock::now();
}

Env::~Env() = default;

void Env::boot(const std::string &configId) {
    LOG(debug, "Reading configuration for ID: %s", configId.c_str());
    _cfgOwner.subscribe(configId, CONFIG_TIMEOUT_MS);
    bool ok = _cfgOwner.checkForConfigUpdate();
    // subscribe() should throw if something is not OK
    LOG_ASSERT(ok && _cfgOwner.hasConfig());
    const auto & cfg = _cfgOwner.getConfig();
    LOG(config, "Booting sentinel '%s' with [stateserver port %d] and [rpc port %d]",
        configId.c_str(), cfg.port.telnet, cfg.port.rpc);
    rpcPort(cfg.port.rpc);
    statePort(cfg.port.telnet);
    if (auto up = ConfigOwner::fetchModelConfig(MODEL_TIMEOUT_MS)) {
        const ModelConfig &model = *up;
        for (const auto & h : model.hosts) {
            LOG(info, "- Model for host %s with %zd services", h.name.c_str(), h.services.size());
            for (const auto & s : h.services) {       
                if (s.name == "config-sentinel") { 
                    LOG(info, "  - Model for service %s type %s configid %s with %zd ports",
                        s.name.c_str(), s.type.c_str(), s.configid.c_str(), s.ports.size());
                    for (const auto & p : s.ports) {
                        if (p.tags.find("rpc") != p.tags.npos) {
                            LOG(info, "    - Model for port %d has tags %s", p.number, p.tags.c_str());
                        }
                    }
                }
            }
        }
    }
}

void Env::rpcPort(int port) {
    if (port < 0 || port > 65535) {
        throw vespalib::FatalException("Bad port " + std::to_string(port) + ", expected range [1, 65535]", VESPA_STRLOC);
    }
    if (port == 0) {
        port = 19097; // default in config
    }
    if (_rpcServer && port == _rpcServer->getPort()) {
        return; // ok already
    }
    _rpcServer = std::make_unique<RpcServer>(port, _rpcCommandQueue);
}

void Env::statePort(int port) {
    if (port < 0 || port > 65535) {
        throw vespalib::FatalException("Bad port " + std::to_string(port) + ", expected range [1, 65535]", VESPA_STRLOC);
    }
    if (port == 0) {
        port = 19098;
    }
    if (_stateServer && port == _statePort) {
        return; // ok already
    }
    LOG(debug, "Config-sentinel accepts connections on port %d", port);
    _stateServer = std::make_unique<vespalib::StateServer>(
        port, _stateApi.myHealth, _startMetrics.producer, _stateApi.myComponents);
    _statePort = port;
}

void Env::notifyConfigUpdated() {
    vespalib::ComponentConfigProducer::Config current("sentinel", _cfgOwner.getGeneration(), "ok");
    _stateApi.myComponents.addConfig(current);

}

void Env::handleCmd(Cmd::UP cmd) {
    cmd->retError("still booting, not ready for all RPC commands");
}

}
