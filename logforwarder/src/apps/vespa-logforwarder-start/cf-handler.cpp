// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cf-handler.h"
#include <vespa/config/common/configsystem.h>
#include <vespa/config/common/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".cf-handler");

CfHandler::CfHandler() : _subscriber() {}

CfHandler::~CfHandler()
{
}

void
CfHandler::subscribe(const std::string & configId, uint64_t timeoutMS)
{
    _handle = _subscriber.subscribe<LogforwarderConfig>(configId, timeoutMS);
}

void
CfHandler::doConfigure()
{
    std::unique_ptr<LogforwarderConfig> cfg(_handle->getConfig());
    const LogforwarderConfig& config(*cfg);

    printf("logforwarder type: %s\n", config.type.c_str());
    for (unsigned int i = 0; i < config.sources.size(); ++i) {
        printf("source %d  is: %s\n", i, config.sources[i].log.c_str());
    }
    printf("endpoint: %s\n", config.endpoint.c_str());
    printf("index: %s\n", config.index.c_str());
}

void
CfHandler::check()
{
    if (_subscriber.nextConfig(0)) {
        doConfigure();
    }
}

constexpr uint64_t CONFIG_TIMEOUT_MS = 30 * 1000;

void
CfHandler::start(const char *configId)
{
    LOG(debug, "Reading configuration configid '%s'", configId);
    try {
        subscribe(configId, CONFIG_TIMEOUT_MS);
    } catch (config::ConfigTimeoutException & ex) {
        LOG(warning, "Timout getting config, please check your setup. Will exit and restart: %s", ex.getMessage().c_str());
        exit(EXIT_FAILURE);
    } catch (config::InvalidConfigException& ex) {
        LOG(error, "Fatal: Invalid configuration, please check your setup: %s", ex.getMessage().c_str());
        exit(EXIT_FAILURE);
    } catch (config::ConfigRuntimeException& ex) {
        LOG(error, "Fatal: Could not get config, please check your setup: %s", ex.getMessage().c_str());
        exit(EXIT_FAILURE);
    }
    doConfigure();
}
