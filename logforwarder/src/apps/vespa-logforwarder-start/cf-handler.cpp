// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cf-handler.h"
#include <vespa/config/common/configsystem.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/subscription/configsubscriber.hpp>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".cf-handler");

CfHandler::CfHandler() = default;

CfHandler::~CfHandler() = default;

void CfHandler::subscribe(const std::string & configId, std::chrono::milliseconds timeout) {
    _handle = _subscriber.subscribe<LogforwarderConfig>(configId, timeout);
}

namespace {
bool isExecutable(const char *path) {
    struct stat statbuf;
    if (stat(path, &statbuf) != 0) {
        return false;
    }
    if (! S_ISREG(statbuf.st_mode)) {
        return false;
    }
    return ((statbuf.st_mode & S_IXOTH) != 0);
}

time_t lastModTime(const vespalib::string &fn) {
    if (fn.empty()) return 0;
    struct stat info;
    if (stat(fn.c_str(), &info) != 0) return 0;
    return info.st_mtime;
}

} // namespace

void CfHandler::doConfigure() {
    _lastConfig = _handle->getConfig();
    const LogforwarderConfig& config(*_lastConfig);
    LOG(debug, "validating splunk home '%s'", config.splunkHome.c_str());
    auto program = config.splunkHome + "/bin/splunk";
    if (isExecutable(program.c_str())) {
        gotConfig(config);
    } else {
        LOG(warning, "invalid splunk home, '%s' is not an executable", program.c_str());
    }
}

vespalib::string CfHandler::clientCertFile() const {
    static const vespalib::string certDir = "/var/lib/sia/certs/";
    if (_lastConfig && !_lastConfig->role.empty()) {
        return certDir + _lastConfig->role + ".cert.pem";
    }
    return "";
}

vespalib::string CfHandler::clientKeyFile() const {
    static const vespalib::string certDir = "/var/lib/sia/keys/";
    if (_lastConfig && !_lastConfig->role.empty()) {
        return certDir + _lastConfig->role + ".key.pem";
    }
    return "";
}

bool CfHandler::certFileChanged() {
    time_t modTime = lastModTime(clientCertFile());
    if (modTime != _lastCertFileChange) {
        _lastCertFileChange = modTime;
        return true;
    }
    return false;
}

void
CfHandler::check()
{
    if (_subscriber.nextConfigNow() || certFileChanged()) {
        doConfigure();
    }
}

constexpr std::chrono::milliseconds CONFIG_TIMEOUT_MS(30 * 1000);

void
CfHandler::start(const char *configId)
{
    LOG(debug, "Reading configuration with id '%s'", configId);
    try {
        subscribe(configId, CONFIG_TIMEOUT_MS);
    } catch (config::ConfigTimeoutException & ex) {
        LOG(warning, "Timout getting config, please check your setup. Will exit and restart: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    } catch (config::InvalidConfigException& ex) {
        LOG(error, "Fatal: Invalid configuration, please check your setup: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    } catch (config::ConfigRuntimeException& ex) {
        LOG(error, "Fatal: Could not get config, please check your setup: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    }
}
