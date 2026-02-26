// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connectivity.h"

#include "config-owner.h"
#include "outward-check.h"

#include <vespa/defaults.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <chrono>
#include <thread>

#include <vespa/log/log.h>

LOG_SETUP(".sentinel.connectivity");

using vespalib::make_string_short::fmt;
using namespace std::chrono_literals;

namespace config::sentinel {

Connectivity::Connectivity() = default;
Connectivity::~Connectivity() = default;

namespace {

std::string toString(CcResult value) {
    switch (value) {
    case CcResult::UNKNOWN:
        return "BAD: missing result"; // very very bad
    case CcResult::INDIRECT_PING_FAIL:
        return "connect OK, but reverse check FAILED"; // very bad
    case CcResult::UNREACHABLE_UP:
        return "unreachable from me, but up"; // very bad
    case CcResult::CONN_FAIL:
        return "failed to connect"; // bad
    case CcResult::INDIRECT_PING_UNAVAIL:
        return "connect OK (but reverse check unavailable)"; // unfortunate
    case CcResult::ALL_OK:
        return "OK: both ways connectivity verified"; // good
    }
    LOG(error, "Unknown CcResult enum value: %d", (int)value);
    LOG_ABORT("Unknown CcResult enum value");
}

using ConnectivityMap = std::map<std::string, OutwardCheck>;
using HostAndPort = Connectivity::HostAndPort;
using SpecMap = Connectivity::SpecMap;

std::string spec(const SpecMap::value_type& host_and_port) {
    return fmt("tcp/%s:%d", host_and_port.first.c_str(), host_and_port.second);
}

void classifyConnFails(ConnectivityMap& connectivityMap, const SpecMap& specMap, RpcServer& rpcServer) {
    std::vector<HostAndPort> failedConnSpecs;
    std::vector<HostAndPort> goodNeighborSpecs;
    std::string              myHostname = vespa::Defaults::vespaHostname();
    for (auto& [hostname, check] : connectivityMap) {
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
    for (const auto& toClassify : failedConnSpecs) {
        const auto& [nameToCheck, portToCheck] = toClassify;
        auto cmIter = connectivityMap.find(nameToCheck);
        LOG_ASSERT(cmIter != connectivityMap.end());
        OutwardCheckContext cornerContext(goodNeighborSpecs.size(), nameToCheck, portToCheck, rpcServer.orb());
        ConnectivityMap     cornerProbes;
        int                 ping_timeout = 1000 + 50 * goodNeighborSpecs.size();
        for (const auto& hp : goodNeighborSpecs) {
            cornerProbes.try_emplace(hp.first, spec(hp), cornerContext, ping_timeout);
        }
        cornerContext.latch.await();
        size_t numReportsUp = 0;
        size_t numReportsDown = 0;
        for (const auto& [hostname, probe] : cornerProbes) {
            if (probe.result() == CcResult::INDIRECT_PING_FAIL)
                ++numReportsDown;
            if (probe.result() == CcResult::ALL_OK)
                ++numReportsUp;
        }
        if (numReportsUp > 0) {
            LOG(debug, "Unreachable: %s is up according to %zd hosts (down according to me + %zd others)",
                nameToCheck.c_str(), numReportsUp, numReportsDown);
            OutwardCheckContext reverseContext(1, myHostname, rpcServer.getPort(), rpcServer.orb());
            OutwardCheck        check(spec(toClassify), reverseContext, 1000);
            reverseContext.latch.await();
            auto secondResult = check.result();
            if (secondResult == CcResult::CONN_FAIL) {
                cmIter->second.classifyResult(CcResult::UNREACHABLE_UP);
            } else {
                LOG(debug, "Recheck %s gives new result: %s", nameToCheck.c_str(), toString(secondResult).c_str());
                cmIter->second.classifyResult(secondResult);
            }
        }
    }
}

} // namespace

SpecMap Connectivity::specsFrom(const ModelConfig& model) {
    SpecMap checkSpecs;
    for (const auto& h : model.hosts) {
        bool foundSentinelPort = false;
        for (const auto& s : h.services) {
            if (s.name == "config-sentinel") {
                for (const auto& p : s.ports) {
                    if (p.tags.find("rpc") != p.tags.npos) {
                        checkSpecs[h.name] = p.number;
                        foundSentinelPort = true;
                    }
                }
            }
        }
        if (!foundSentinelPort) {
            LOG(warning, "Did not find 'config-sentinel' RPC port in model for host %s [%zd services]", h.name.c_str(),
                h.services.size());
        }
    }
    return checkSpecs;
}

void Connectivity::configure(const SentinelConfig::Connectivity& config, const ModelConfig& model) {
    _config = config;
    LOG(config, "connectivity.maxBadCount = %d", _config.maxBadCount);
    LOG(config, "connectivity.minOkPercent = %d", _config.minOkPercent);
    LOG(config, "connectivity.ignore = %s", _config.ignore ? "true" : "false");
    _checkSpecs = specsFrom(model);
}

bool Connectivity::checkConnectivity(RpcServer& rpcServer) {
    size_t clusterSize = _checkSpecs.size();
    if (clusterSize == 0) {
        LOG(warning, "could not get model config, skipping connectivity checks");
        return true;
    }
    std::string         myHostname = vespa::Defaults::vespaHostname();
    OutwardCheckContext checkContext(clusterSize, myHostname, rpcServer.getPort(), rpcServer.orb());
    ConnectivityMap     connectivityMap;
    int                 ping_timeout = 1000 + 50 * _checkSpecs.size();
    for (const auto& host_and_port : _checkSpecs) {
        connectivityMap.try_emplace(host_and_port.first, spec(host_and_port), checkContext, ping_timeout);
    }
    checkContext.latch.await();
    classifyConnFails(connectivityMap, _checkSpecs, rpcServer);
    Accumulator accumulated;
    for (const auto& [hostname, check] : connectivityMap) {
        std::string detail = toString(check.result());
        std::string prev = _detailsPerHost[hostname];
        if (prev != detail) {
            LOG(info, "Connectivity check details: %s -> %s", hostname.c_str(), detail.c_str());
        }
        _detailsPerHost[hostname] = detail;
        LOG_ASSERT(check.result() != CcResult::UNKNOWN);
        accumulated.handleResult(check.result());
    }
    return accumulated.enoughOk(_config);
}

void Connectivity::Accumulator::handleResult(CcResult value) {
    ++_numHandled;
    switch (value) {
    case CcResult::UNKNOWN:
    case CcResult::UNREACHABLE_UP:
    case CcResult::INDIRECT_PING_FAIL:
        ++_numBad;
        break;
    case CcResult::CONN_FAIL:
        // not OK, but not a serious issue either
        break;
    case CcResult::INDIRECT_PING_UNAVAIL:
    case CcResult::ALL_OK:
        ++_numOk;
        break;
    }
}

bool Connectivity::Accumulator::enoughOk(const SentinelConfig::Connectivity& config) const {
    bool enough = true;
    if (_numBad > size_t(config.maxBadCount)) {
        LOG(warning, "%zu of %zu nodes up but with network connectivity problems (max is %d)", _numBad, _numHandled,
            config.maxBadCount);
        enough = false;
    }
    if (_numOk * 100.0 < config.minOkPercent * _numHandled) {
        double pct = _numOk * 100.0 / _numHandled;
        LOG(warning, "Only %zu of %zu nodes are up and OK, %.1f%% (min is %d%%)", _numOk, _numHandled, pct,
            config.minOkPercent);
        enough = false;
    }
    if (config.ignore && !enough) {
        LOG(warning, "Connectivity checks ignored, proceeding with service startup anyway");
        enough = true;
    }
    if (_numOk == _numHandled) {
        LOG(info, "All connectivity checks OK, proceeding with service startup");
    } else if (enough) {
        LOG(info, "Enough connectivity checks OK, proceeding with service startup");
    }
    return enough;
}

} // namespace config::sentinel
