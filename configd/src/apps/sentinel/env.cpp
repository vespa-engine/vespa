// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "env.h"
#include "check-completion-handler.h"
#include "connectivity.h"
#include <vespa/defaults.h>
#include <vespa/log/log.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>
#include <chrono>

LOG_SETUP(".env");

using vespalib::make_string_short::fmt;
using namespace std::chrono_literals;

namespace config::sentinel {

namespace {

void maybeStopNow() {
    if (vespalib::SignalHandler::INT.check() ||
        vespalib::SignalHandler::TERM.check())
    {
        throw vespalib::FatalException("got signal during boot()");
    }
}

constexpr std::chrono::milliseconds CONFIG_TIMEOUT_MS = 3min;
constexpr std::chrono::milliseconds MODEL_TIMEOUT_MS = 1500ms;

} // namespace <unnamed>

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
    _stateApi.myHealth.setFailed("initializing...");
}

Env::~Env() = default;

void Env::boot(const std::string &configId) {
    LOG(debug, "Reading configuration for ID: %s", configId.c_str());
    _cfgOwner.subscribe(configId, CONFIG_TIMEOUT_MS);
    // subscribe() should throw if something is not OK
    for (int retry = 0; retry < maxRetryLoops; ++retry) {
        _cfgOwner.checkForConfigUpdate();
        LOG_ASSERT(_cfgOwner.hasConfig());
        const auto & cfg = _cfgOwner.getConfig();
        LOG(config, "Booting sentinel '%s' with [stateserver port %d] and [rpc port %d]",
            configId.c_str(), cfg.port.telnet, cfg.port.rpc);
        rpcPort(cfg.port.rpc);
        statePort(cfg.port.telnet);
        if (waitForConnectivity(retry)) {
            _stateApi.myHealth.setOk();
            return;
        } else {
            LOG(warning, "Bad network connectivity, retry from start");
        }
    }
    throw InvalidConfigException("Giving up - too many connectivity check failures");
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

bool Env::waitForConnectivity(int outerRetry) {
    auto up = ConfigOwner::fetchModelConfig(MODEL_TIMEOUT_MS);
    if (! up) {
        LOG(warning, "could not get model config, skipping connectivity checks");
        return true;
    }
    Connectivity::CheckResult lastCheckResult;
    Connectivity checker(_cfgOwner.getConfig().connectivity, *_rpcServer);
    for (int retry = 0; retry < maxRetriesInsideLoop; ++retry) {
        auto res = checker.checkConnectivity(*up);
        if (res.enoughOk) {
            LOG(info, "Connectivity check OK, proceeding with service startup");
            if (retry > 0 || ! res.allOk) {
                res.logDetails();
            }
            return true;
        }
        LOG(warning, "Connectivity check FAILED (try %d)", 1 + retry + maxRetriesInsideLoop*outerRetry);
        _stateApi.myHealth.setFailed("FAILED connectivity check");
        if (lastCheckResult.details != res.details) {
            res.logDetails();
            lastCheckResult = std::move(res);
        }
        for (int i = 0; i <= outerRetry; ++i) {
            respondAsEmpty();
            maybeStopNow();
            std::this_thread::sleep_for(1s);
        }
    }
    return false;
}

}
