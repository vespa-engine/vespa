// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace config {

class ConfigSystem {
public:
    ConfigSystem();
    bool isUp() const;
private:
    bool isConfigProxyRunning() const;
    vespalib::string _configProxyPidFile;
};

}

