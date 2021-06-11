// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rpcserver.h"
#include "cc-result.h"
#include <vespa/config-sentinel.h>
#include <vespa/config-model.h>
#include <string>
#include <map>

using cloud::config::SentinelConfig;
using cloud::config::ModelConfig;

namespace config::sentinel {

/**
 * Utility class for running connectivity check.
 **/
class Connectivity {
public:
    using SpecMap = std::map<std::string, int>;
    using HostAndPort = SpecMap::value_type;

    Connectivity();
    ~Connectivity();
    void configure(const SentinelConfig::Connectivity &config,
                   const ModelConfig &model);
    bool checkConnectivity(RpcServer &rpcServer);
    static SpecMap specsFrom(const ModelConfig &model);
private:
    struct Accumulated {
        size_t numIssues = 0;
        size_t numSeriousIssues = 0;
    };
    void accumulate(Accumulated &target, CcResult value);
    bool enoughOk(const Accumulated &results, size_t clusterSize);
    SentinelConfig::Connectivity _config;
    SpecMap _checkSpecs;
    std::map<std::string, std::string> _detailsPerHost;
};

}
