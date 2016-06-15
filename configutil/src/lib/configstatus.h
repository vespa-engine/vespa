// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-model.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/config.h>

class ConfigStatus
{
public:
    struct Flags {
        bool verbose;
        Flags()
            : verbose(false)
        {}
    };

    ConfigStatus(Flags flags, const config::ConfigUri uri);
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

