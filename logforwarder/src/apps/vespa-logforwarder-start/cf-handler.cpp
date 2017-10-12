// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cf-handler.h"
#include <dirent.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <vespa/defaults.h>
#include <vespa/config/common/configsystem.h>
#include <vespa/config/common/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".cf-handler");

CfHandler::CfHandler() : _childRunning(false), _subscriber() {}

CfHandler::~CfHandler()
{
}

void
CfHandler::subscribe(const std::string & configId, uint64_t timeoutMS)
{
    _handle = _subscriber.subscribe<LogforwarderConfig>(configId, timeoutMS);
}

namespace {

bool fixDir(const vespalib::string &path) {
    if (path.size() == 0) return true;
    size_t lastSlash = path.rfind('/');
    if (lastSlash != vespalib::string::npos) {
        vespalib::string parent = path.substr(0, lastSlash);
        if (!fixDir(parent)) return false;
    }
    DIR *dp = opendir(path.c_str());
    if (dp == NULL) {
        if (errno != ENOENT || mkdir(path.c_str(), 0755) != 0) {
            perror(path.c_str());
            return false;
        }
    } else {
        closedir(dp);
    }
    return true;
}

vespalib::string
cfFilePath(const vespalib::string &parent) {
    vespalib::string path = parent + "/etc/system/local";
    fixDir(path);
    path += "/deploymentclient.conf";
    return path;
}

void
runSplunk(const vespalib::string &prefix, const char *a1, const char *a2 = 0)
{
    const char *argv[] = { 0, a1, a2, 0 };
    vespalib::string path = prefix + "/bin/splunk";
    argv[0] = path.c_str();
    fprintf(stdout, "starting splunk forwarder with command: '%s' '%s'\n",
            argv[0], argv[1]);
    if (fork() == 0) {
        vespalib::string env = "SPLUNK_HOME=" + prefix;
        char *cenv = const_cast<char *>(env.c_str());
        putenv(cenv);
        char **cargv = const_cast<char **>(argv);
        execv(argv[0], cargv);
        // if execv fails:
        perror(argv[0]);
        exit(1);
    }
}

} // namespace <unnamed>

void
CfHandler::doConfigure()
{
    std::unique_ptr<LogforwarderConfig> cfg(_handle->getConfig());
    const LogforwarderConfig& config(*cfg);

    vespalib::string path = cfFilePath(config.splunkPath);
    vespalib::string tmpPath = path + ".new";
    FILE *fp = fopen(tmpPath.c_str(), "w");
    if (fp == NULL) return;

    fprintf(fp, "[deployment-client]\n");
    fprintf(fp, "clientName = %s\n", config.clientName.c_str());
    fprintf(fp, "\n");
    fprintf(fp, "[target-broker:deploymentServer]\n");
    fprintf(fp, "targetUri = %s\n", config.deploymentServer.c_str());

    fclose(fp);
    rename(tmpPath.c_str(), path.c_str());

    startChild(config.splunkPath);
}

void
CfHandler::startChild(const vespalib::string &prefix)
{
    if (_childRunning) {
        runSplunk(prefix, "restart");
    } else {
        runSplunk(prefix, "start", "--accept-license");
        _childRunning = true;
    }
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
    LOG(debug, "Reading configuration with id '%s'", configId);
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
}
