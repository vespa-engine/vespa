// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hostfilter.h"
#include <vespa/config-model.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/subscription/configuri.h>

class ConfigStatus
{
public:
    struct Flags {
        HostFilter host_filter;
        bool verbose;
        Flags()
            : host_filter(), verbose(false)
        {}

        explicit Flags(const HostFilter& filter)
            : host_filter(filter),
              verbose(false)
        {}
    };

    ConfigStatus(Flags flags, const config::ConfigUri &uri);
    ~ConfigStatus();
    int action();

private:
    std::unique_ptr<cloud::config::ModelConfig> _cfg;
    Flags                              _flags;
    int64_t                            _generation;

    bool fetch_json(std::string configId, std::string host, int port, std::string path,
                    std::string &data);
    bool checkServiceGeneration(std::string configId, std::string host, int port,
                                std::string path);
};

