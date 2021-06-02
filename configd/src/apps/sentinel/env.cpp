// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "env.h"
#include "check-completion-handler.h"
#include "outward-check.h"
#include <vespa/defaults.h>
#include <vespa/log/log.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>
#include <chrono>

LOG_SETUP(".env");

using vespalib::make_string_short::fmt;
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
        waitForConnectivity(*up);
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
        port = 19098; // default in config
    }
    if (_stateServer && port == _statePort) {
        return; // ok already
    }
    LOG(debug, "Config-sentinel accepts state connections on port %d", port);
    _stateServer = std::make_unique<vespalib::StateServer>(
        port, _stateApi.myHealth, _startMetrics.producer, _stateApi.myComponents);
    _statePort = port;
}

void Env::notifyConfigUpdated() {
    vespalib::ComponentConfigProducer::Config current("sentinel", _cfgOwner.getGeneration(), "ok");
    _stateApi.myComponents.addConfig(current);

}

void Env::respondAsEmpty() {
    auto commands = _rpcCommandQueue.drain();
    for (Cmd::UP &cmd : commands) {
        cmd->retError("still booting, not ready for all RPC commands");
    }
}

namespace {

const char *toString(CcResult value) {
    switch (value) {
        case CcResult::UNKNOWN: return "unknown";
        case CcResult::CONN_FAIL: return "failed to connect";
        case CcResult::REVERSE_FAIL: return "connect OK, but reverse check FAILED";
        case CcResult::REVERSE_UNAVAIL: return "connect OK, but reverse check unavailable";
        case CcResult::ALL_OK: return "both ways connectivity OK";
    }
    LOG(error, "Unknown CcResult enum value: %d", (int)value);
    LOG_ABORT("Unknown CcResult enum value");
}

std::map<std::string, std::string> specsFrom(const ModelConfig &model) {
    std::map<std::string, std::string> checkSpecs;
    for (const auto & h : model.hosts) {
        bool foundSentinelPort = false;
        for (const auto & s : h.services) {
            if (s.name == "config-sentinel") {
                for (const auto & p : s.ports) {
                    if (p.tags.find("rpc") != p.tags.npos) {
                        auto spec = fmt("tcp/%s:%d", h.name.c_str(), p.number);
                        checkSpecs[h.name] = spec;
                        foundSentinelPort = true;
                    }
                }
            }
        }
        if (! foundSentinelPort) {
            LOG(warning, "Did not find 'config-sentinel' RPC port in model for host %s [%zd services]",
                h.name.c_str(), h.services.size());
        }
    }
    return checkSpecs;
}

}

void Env::waitForConnectivity(const ModelConfig &model) {
    auto checkSpecs = specsFrom(model);
    OutwardCheckContext checkContext(checkSpecs.size(),
                                     vespa::Defaults::vespaHostname(),
                                     _rpcServer->getPort(),
                                     _rpcServer->orb());
    std::map<std::string, OutwardCheck> connectivityMap;
    for (const auto & [ hn, spec ] : checkSpecs) {
        connectivityMap.try_emplace(hn, spec, checkContext);
    }
    checkContext.latch.await();
    for (const auto & [hostname, check] : connectivityMap) {
        LOG(info, "outward check status for host %s is: %s",
            hostname.c_str(), toString(check.result()));
    }
}

}
