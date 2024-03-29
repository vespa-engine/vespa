// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketcopy.h>

namespace vespalib { class asciistream; }

namespace storage::distributor {

struct MergeMetaData {
    uint16_t _nodeIndex;
    bool _sourceOnly;
    const BucketCopy* _copy;

    MergeMetaData() noexcept : _nodeIndex(0), _sourceOnly(false), _copy(nullptr) {}
    MergeMetaData(uint16_t nodeIndex, const BucketCopy& copy) noexcept
        : _nodeIndex(nodeIndex), _sourceOnly(false), _copy(&copy) {}

    [[nodiscard]] bool trusted() const noexcept {
        return _copy->trusted();
    }
    [[nodiscard]] uint32_t checksum() const noexcept {
        return _copy->getChecksum();
    }
    [[nodiscard]] bool source_only() const noexcept { return _sourceOnly; }
};

vespalib::asciistream& operator<<(vespalib::asciistream& out, const MergeMetaData& e);

}
