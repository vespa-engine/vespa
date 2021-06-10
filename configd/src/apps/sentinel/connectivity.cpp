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
        case CcResult::INDIRECT_PING_FAIL: return "connect OK, but reverse check FAILED"; // very bad
        case CcResult::UNREACHABLE_UP: return "unreachable from me, but up"; // very bad
        case CcResult::CONN_FAIL: return "failed to connect"; // bad
        case CcResult::INDIRECT_PING_UNAVAIL: return "connect OK (but reverse check unavailable)"; // unfortunate
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

void classifyConnFails(ConnectivityMap &connectivityMap,
                       const SpecMap &specMap,
                       RpcServer &rpcServer)
{
    std::vector<HostAndPort> failedConnSpecs;
    std::vector<HostAndPort> goodNeighborSpecs;
    std::string myHostname = vespa::Defaults::vespaHostname();
    for (auto & [hostname, check] : connectivityMap) {
        if (hostname == myHostname) {
            if (check.result() == CcResult::CONN_FAIL) {
                check.classifyResult(CcResult::UNREACHABLE_UP);
            }
        } else {
            auto iter = specMap.find(hostname);
            LOG_ASSERT(iter != specMap.end());
            if (check.result() == CcResult::ALL_OK) {
                goodNeighborSpecs.push_back(*iter);
            }
            if (check.result() == CcResult::CONN_FAIL) {
                failedConnSpecs.push_back(*iter);
            }
        }
    }
    if ((failedConnSpecs.size() == 0) || (goodNeighborSpecs.size() == 0)) {
        return;
    }
    for (const auto & [ nameToCheck, portToCheck ] : failedConnSpecs) {
        auto cmIter = connectivityMap.find(nameToCheck);
        LOG_ASSERT(cmIter != connectivityMap.end());
        OutwardCheckContext checkContext(goodNeighborSpecs.size(), nameToCheck, portToCheck, rpcServer.orb());
        ConnectivityMap cornerProbes;
        for (const auto & hp : goodNeighborSpecs) {
            cornerProbes.try_emplace(hp.first, spec(hp), checkContext);
        }
        checkContext.latch.await();
        size_t numReportsUp = 0;
        size_t numReportsDown = 0;
        for (const auto & [hostname, probe] : cornerProbes) {
            if (probe.result() == CcResult::INDIRECT_PING_FAIL) ++numReportsDown;
            if (probe.result() == CcResult::ALL_OK) ++numReportsUp;
        }
        if (numReportsUp > 0) {
            LOG(debug, "Unreachable: %s is up according to %zd hosts (down according to me + %zd others)",
                nameToCheck.c_str(), numReportsUp, numReportsDown);
            cmIter->second.classifyResult(CcResult::UNREACHABLE_UP);
        }
    }
}

} // namespace <unnamed>

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
    classifyConnFails(connectivityMap, _checkSpecs, rpcServer);
    Accumulated accumulated;
    for (const auto & [hostname, check] : connectivityMap) {
        std::string detail = toString(check.result());
        std::string prev = _detailsPerHost[hostname];
        if (prev != detail) {
            LOG(info, "Connectivity check details: %s -> %s", hostname.c_str(), detail.c_str());
        }
        _detailsPerHost[hostname] = detail;
        LOG_ASSERT(check.result() != CcResult::UNKNOWN);
        accumulate(accumulated, check.result());
    }
    return enoughOk(accumulated, clusterSize);
}

void Connectivity::accumulate(Accumulated &target, CcResult value) {
    switch (value) {
        case CcResult::UNKNOWN:
        case CcResult::UNREACHABLE_UP:
        case CcResult::INDIRECT_PING_FAIL:
            ++target.numSeriousIssues;
            ++target.numIssues;
            break;
        case CcResult::CONN_FAIL:
            ++target.numIssues;
            break;
        case CcResult::INDIRECT_PING_UNAVAIL:
        case CcResult::ALL_OK:
            break;
    }
}

bool Connectivity::enoughOk(const Accumulated &results, size_t clusterSize) {
    bool enough = true;
    if (results.numSeriousIssues > size_t(_config.maxBadReverseCount)) {
        LOG(warning, "%zu of %zu nodes up but with network connectivity problems (max is %d)",
            results.numSeriousIssues, clusterSize, _config.maxBadReverseCount);
        enough = false;
    }
    if (results.numIssues * 100.0 > _config.maxBadOutPercent * clusterSize) {
        double pct = results.numIssues * 100.0 / clusterSize;
        LOG(warning, "Problems with connection to %zu of %zu nodes, %.1f%% (max is %d%%)",
            results.numIssues, clusterSize, pct, _config.maxBadOutPercent);
        enough = false;
    }
    if (results.numIssues == 0) {
        LOG(info, "All connectivity checks OK, proceeding with service startup");
    } else if (enough) {
        LOG(info, "Enough connectivity checks OK, proceeding with service startup");
    }
    return enough;
}

}
