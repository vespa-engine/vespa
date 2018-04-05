// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/slobrok/cfg.h>
#include <string>

namespace slobrok {

class ConfigShim
{
private:
    uint32_t _port;
    uint32_t _statePort;
    std::string _configId;
    ConfiguratorFactory _factory;

public:
    ConfigShim(uint32_t port);
    ConfigShim(uint32_t port, uint32_t statePort_in, const std::string& cfgId);
    ConfigShim(uint32_t port, const std::string& cfgId, config::IConfigContext::SP cfgCtx);
    ~ConfigShim();

    uint32_t portNumber() const { return _port; }
    uint32_t statePort() const { return _statePort; }
    std::string configId() const { return _configId; }
    const char *id() const { return _configId.c_str(); }
    const ConfiguratorFactory & factory() const { return _factory; }
};

} // namespace slobrok
