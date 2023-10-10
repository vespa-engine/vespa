// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/frt/protocol.h>

namespace config {

struct CompressionInfo {
    CompressionInfo();
    CompressionType compressionType;
    uint32_t uncompressedSize;
    void deserialize(const vespalib::slime::Inspector & inspector);
};

}

