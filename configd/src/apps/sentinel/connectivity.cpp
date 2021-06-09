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

std::string toString(CcResult value) {
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

using ConnectivityMap = std::map<std::string, OutwardCheck>;
using HostAndPort = Connectivity::HostAndPort;
using SpecMap = Connectivity::SpecMap;

std::string spec(const SpecMap::value_type &host_and_port) {
    return fmt("tcp/%s:%d", host_and_port.first.c_str(), host_and_port.second);
}

SpecMap specsFrom(const ModelConfig &model) {
    SpecMap checkSpecs;
    for (const auto & h : model.hosts) {
        bool foundSentinelPort = false;
        for (const auto & s : h.services) {
            if (s.name == "config-sentinel") {
                for (const auto & p : s.ports) {
                    if (p.tags.find("rpc") != p.tags.npos) {
                        checkSpecs[h.name] = p.number;
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

size_t countUnreachable(const ConnectivityMap &connectivityMap,
                        const SpecMap &specMap,
                        RpcServer &rpcServer)
{
    std::vector<HostAndPort> failedConnSpecs;
    std::vector<HostAndPort> goodNeighborSpecs;
    std::string myHostname = vespa::Defaults::vespaHostname();
    for (const auto & [hostname, check] : connectivityMap) {
        auto iter = specMap.find(hostname);
        LOG_ASSERT(iter != specMap.end());
        if ((check.result() == CcResult::ALL_OK) && (hostname != myHostname)) {
            goodNeighborSpecs.push_back(*iter);
        }
        if (check.result() == CcResult::CONN_FAIL) {
            failedConnSpecs.push_back(*iter);
        }
    }
    size_t counter = 0;
    for (const auto & toCheck : failedConnSpecs) {
        OutwardCheckContext checkContext(goodNeighborSpecs.size(), toCheck.first, toCheck.second, rpcServer.orb());
        ConnectivityMap cornerProbes;
        for (const auto & hp : goodNeighborSpecs) {
            cornerProbes.try_emplace(hp.first, spec(hp), checkContext);
        }
        checkContext.latch.await();
        size_t numReportsUp = 0;
        size_t numReportsDown = 0;
        for (const auto & [hostname, probe] : cornerProbes) {
            if (probe.result() == CcResult::REVERSE_FAIL) ++numReportsDown;
            if (probe.result() == CcResult::ALL_OK) ++numReportsUp;
        }
        if (numReportsUp > numReportsDown) {
            LOG(warning, "Unreachable: %s is up according to %zd hosts (down according to me + %zd others)",
                toCheck.first.c_str(), numReportsUp, numReportsDown);
            ++counter;
        }
    }
    return counter;
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
    std::string myHostname = vespa::Defaults::vespaHostname();
    OutwardCheckContext checkContext(clusterSize,
                                     myHostname,
                                     rpcServer.getPort(),
                                     rpcServer.orb());
    ConnectivityMap connectivityMap;
    for (const auto &host_and_port : _checkSpecs) {
        connectivityMap.try_emplace(host_and_port.first, spec(host_and_port), checkContext);
    }
    checkContext.latch.await();
    size_t numAllGood = 0;
    size_t numFailedConns = 0;
    size_t numFailedReverse = 0;
    bool allChecksOk = true;
    for (const auto & [hostname, check] : connectivityMap) {
        std::string detail = toString(check.result());
        std::string prev = _detailsPerHost[hostname];
        if (prev != detail) {
            LOG(info, "Connectivity check details: %s -> %s", hostname.c_str(), detail.c_str());
        }
        _detailsPerHost[hostname] = detail;
        LOG_ASSERT(check.result() != CcResult::UNKNOWN);
        if ((check.result() == CcResult::ALL_OK) && (hostname != myHostname)) {
            ++numAllGood;
        }
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
    size_t numUnreachable = (numFailedConns > 0)
        ? countUnreachable(connectivityMap, _checkSpecs, rpcServer)
        : 0;
    if (numUnreachable > size_t(_config.maxBadReverseCount)) {
        LOG(warning, "%zu of %zu nodes are up but unreachable from me (max is %d)",
            numUnreachable, clusterSize, _config.maxBadReverseCount);
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
