// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rpcserver.h"
#include <vespa/config-sentinel.h>
#include <vespa/config-model.h>
#include <string>
#include <vector>

using cloud::config::SentinelConfig;
using cloud::config::ModelConfig;

namespace config::sentinel {

/**
 * Utility class for running connectivity check.
 **/
class Connectivity {
public:
    Connectivity(const SentinelConfig::Connectivity & config, RpcServer &rpcServer);
    ~Connectivity();

    struct CheckResult {
        bool enoughOk;
        bool allOk;
        std::vector<std::string> details;
    };

    CheckResult checkConnectivity(const ModelConfig &model);
private:
    const SentinelConfig::Connectivity _config;
    RpcServer &_rpcServer;
};

}
