// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-owner.h"
#include "connectivity.h"
#include "outward-check.h"
#include <vespa/defaults.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>
#include <chrono>

LOG_SETUP(".connectivity");

using vespalib::make_string_short::fmt;
using namespace std::chrono_literals;

namespace config::sentinel {

constexpr std::chrono::milliseconds MODEL_TIMEOUT_MS = 60s;

Connectivity::Connectivity() = default;
Connectivity::~Connectivity() = default;

namespace {

const char *toString(CcResult value) {
    switch (value) {
        case CcResult::UNKNOWN: return "BAD: missing result"; // very very bad
        case CcResult::REVERSE_FAIL: return "connect OK, but reverse check FAILED"; // very bad
        case CcResult::CONN_FAIL: return "failed to connect"; // bad
        case CcResult::REVERSE_UNAVAIL: return "connect OK (but reverse check unavailable)"; // unfortunate
        case CcResult::ALL_OK: return "OK: both ways connectivity verified"; // good
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

void Connectivity::configure(const SentinelConfig::Connectivity &config) {
    _config = config;
    LOG(config, "connectivity.maxBadReverseCount = %d", _config.maxBadReverseCount);
    LOG(config, "connectivity.maxBadOutPercent = %d", _config.maxBadOutPercent);
    if (auto up = ConfigOwner::fetchModelConfig(MODEL_TIMEOUT_MS)) {
        _checkSpecs = specsFrom(*up);
    }
}

bool
Connectivity::checkConnectivity(RpcServer &rpcServer) {
    size_t clusterSize = _checkSpecs.size();
    if (clusterSize == 0) {
        LOG(warning, "could not get model config, skipping connectivity checks");
        return true;
    }
    OutwardCheckContext checkContext(clusterSize,
                                     vespa::Defaults::vespaHostname(),
                                     rpcServer.getPort(),
                                     rpcServer.orb());
    std::map<std::string, OutwardCheck> connectivityMap;
    for (const auto & [ hn, spec ] : _checkSpecs) {
        connectivityMap.try_emplace(hn, spec, checkContext);
    }
    checkContext.latch.await();
    size_t numFailedConns = 0;
    size_t numFailedReverse = 0;
    bool allChecksOk = true;
    for (const auto & [hostname, check] : connectivityMap) {
        const char *detail = toString(check.result());
        std::string prev = _detailsPerHost[hostname];
        if (prev != detail) {
            LOG(info, "Connectivity check details: %s -> %s", hostname.c_str(), detail);
        }
        _detailsPerHost[hostname] = detail;
        LOG_ASSERT(check.result() != CcResult::UNKNOWN);
        if (check.result() == CcResult::CONN_FAIL) ++numFailedConns;
        if (check.result() == CcResult::REVERSE_FAIL) ++numFailedReverse;
    }
    if (numFailedReverse > size_t(_config.maxBadReverseCount)) {
        LOG(warning, "%zu of %zu nodes report problems connecting to me (max is %d)",
            numFailedReverse, clusterSize, _config.maxBadReverseCount);
        allChecksOk = false;
    }
    if (numFailedConns * 100.0 > _config.maxBadOutPercent * clusterSize) {
        double pct = numFailedConns * 100.0 / clusterSize;
        LOG(warning, "Problems connecting to %zu of %zu nodes, %.2f %% (max is %d)",
            numFailedConns, clusterSize, pct, _config.maxBadOutPercent);
        allChecksOk = false;
    }
    if (allChecksOk && (numFailedConns == 0) && (numFailedReverse == 0)) {
        LOG(info, "All connectivity checks OK, proceeding with service startup");
    } else if (allChecksOk) {
        LOG(info, "Enough connectivity checks OK, proceeding with service startup");
    }
    return allChecksOk;
}

}
