// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketcopy.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace distributor {

struct MergeMetaData {
    uint16_t _nodeIndex;
    bool _sourceOnly;
    const BucketCopy* _copy;

    MergeMetaData() : _nodeIndex(0), _sourceOnly(false), _copy(0) {}
    MergeMetaData(uint16_t nodeIndex, const BucketCopy& copy)
        : _nodeIndex(nodeIndex), _sourceOnly(false), _copy(&copy) {}

    bool trusted() const {
        assert(_copy != 0);
        return _copy->trusted();
    }
    uint32_t checksum() const {
        assert(_copy != 0);
        return _copy->getChecksum();
    }
};

inline vespalib::asciistream& operator<<(vespalib::asciistream& out,
                                         const MergeMetaData& e)
{
    return out << "MergeMetaData(" << e._nodeIndex << ")";
}

} // distributor
} // storage

