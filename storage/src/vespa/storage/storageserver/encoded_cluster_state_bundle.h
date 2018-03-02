// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>

namespace storage {

/**
 * Contains an opaque encoded (possibly compressed) representation of a ClusterStateBundle.
 */
struct EncodedClusterStateBundle {
    vespalib::compression::CompressionConfig::Type _compression_type;
    uint32_t _uncompressed_length;
    std::unique_ptr<vespalib::DataBuffer> _buffer;
};

}
