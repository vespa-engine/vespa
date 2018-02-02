// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/stllike/string.h>

namespace search::engine {

struct SourceDescription {
    int listenPort;
    static const vespalib::string protocol;
    SourceDescription(int port) : listenPort(port) {}
};

}

