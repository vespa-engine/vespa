// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/frt/protocol.h>
#include "compressioninfo.h"

using namespace vespalib;
using namespace vespalib::slime;
using namespace config::protocol;

namespace config {

CompressionInfo::CompressionInfo()
    : compressionType(CompressionType::UNCOMPRESSED),
      uncompressedSize(0)
{
}

void
CompressionInfo::deserialize(const Inspector & inspector)
{
    compressionType = stringToCompressionType(inspector["compressionType"].asString().make_string());
    uncompressedSize = inspector["uncompressedSize"].asLong();
}

}
