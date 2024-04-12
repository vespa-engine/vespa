// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrapper.h"
#include "child-handler.h"

#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <vespa/defaults.h>
#include <vespa/log/log.h>
LOG_SETUP(".wrapper");

namespace {

vespalib::string fixDir(const vespalib::string &parent, const vespalib::string &subdir) {
    auto dirname = parent + "/" + subdir;
    DIR *dp = opendir(dirname.c_str());
    if (dp == NULL) {
        if (errno != ENOENT || mkdir(dirname.c_str(), 0755) != 0) {
            LOG(warning, "Could not create directory '%s'", dirname.c_str());
            perror(dirname.c_str());
        }
    } else {
        closedir(dp);
    }
    return dirname;
}

vespalib::string cfFilePath() {
    vespalib::string path = vespa::Defaults::underVespaHome("var/db/vespa");
    path = fixDir(path, "otelcol");
    return path + "/" + "config.yaml";
}

void  writeConfig(const vespalib::string &config, const vespalib::string &path) {
    LOG(info, "got config, writing %s", path.c_str());
    vespalib::string tmpPath = path + ".new";
    FILE *fp = fopen(tmpPath.c_str(), "w");
    if (fp == NULL) {
        LOG(warning, "could not open '%s' for write", tmpPath.c_str());
        return;
    }
    fprintf(fp, "%s\n", config.c_str());
    fclose(fp);
    rename(tmpPath.c_str(), path.c_str());
}

} // namespace <unnamed>

Wrapper::Wrapper(const std::string &configId)
  : CfHandler(configId),
    _childHandler()
{}

Wrapper::~Wrapper() = default;

void Wrapper::stop() {
    _childHandler.stopChild();
}

void Wrapper::check() {
    checkConfig();
    if (_childHandler.checkChild()) {
        LOG(error, "Fatal: child process died unexpectedly");
        std::_Exit(EXIT_FAILURE);
    }
}

void Wrapper::gotConfig(const OpenTelemetryConfig& config) {
    _childHandler.stopChild();
    std::string progPath = vespa::Defaults::underVespaHome("sbin/otelcol-contrib");
    std::string cfPath = cfFilePath();
    writeConfig(config.yaml, cfPath);
    _childHandler.startChild(progPath, cfPath);
}
