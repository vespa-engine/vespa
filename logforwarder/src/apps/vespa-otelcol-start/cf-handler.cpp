// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cf-handler.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/config/common/configsystem.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/config/subscription/configsubscriber.hpp>

#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".cf-handler");

CfHandler::CfHandler(const std::string & configId)
  : _subscriber(std::make_shared<config::ConfigContext>(*config::legacyConfigId2Spec(configId)))
{}

CfHandler::~CfHandler() = default;

void CfHandler::subscribe(const std::string & configId, std::chrono::milliseconds timeout) {
    LOG(info, "subscribe with config id: %s", configId.c_str());
    std::string cfgId(config::legacyConfigId2ConfigId(configId));
    _handle = _subscriber.subscribe<OpenTelemetryConfig>(cfgId, timeout);
}

void CfHandler::doConfigure() {
    auto curConfig = _handle->getConfig();
    if (_lastConfig && *curConfig == *_lastConfig) {
        LOG(info, "same config as last");
        return;
    }
    LOG(info, "new config, trigger restart");
    _lastConfig = std::move(curConfig);
    const OpenTelemetryConfig& config(*_lastConfig);
    LOG(info, "watch %zu files", config.refPaths.size());
    _fileWatcher.init(config.refPaths);
    gotConfig(config);
}

void CfHandler::checkConfig() {
    if (_subscriber.nextConfigNow()) {
        doConfigure();
    } else if (_fileWatcher.anyChanged()) {
        LOG(info, "watched file updated, trigger restart");
        const OpenTelemetryConfig& config(*_lastConfig);
        gotConfig(config);
    }
}

constexpr std::chrono::milliseconds CONFIG_TIMEOUT_MS(30 * 1000);

void CfHandler::start(const std::string &configId) {
    LOG(debug, "Reading configuration with id '%s'", configId.c_str());
    subscribe(configId, CONFIG_TIMEOUT_MS);
}
