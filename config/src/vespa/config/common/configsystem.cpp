// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "configsystem.h"
#include <vespalib/stllike/string.h>
#include <vespa/defaults.h>

namespace config {

namespace {

vespalib::string CONFIG_PROXY_PID_FILE= vespa::Defaults::vespaHome() + vespalib::string("var/run/configproxy.pid");

}

bool ConfigSystem::isUp() const {
    return isConfigProxyRunning();
}

bool ConfigSystem::isConfigProxyRunning() const {
    struct stat fs;

    int retval = stat(CONFIG_PROXY_PID_FILE, &fs);
    if (retval == 0) {
        if (S_ISREG(fs.st_mode)) {
            return true;
        }
    }
    return false;
}

}
