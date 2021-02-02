// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage {

class BucketCopy {
private:
    uint64_t _timestamp;
    api::BucketInfo _info;
    uint16_t _flags;
    uint16_t _node;

public:
    static const int TRUSTED = 1;

    BucketCopy() noexcept
        : _timestamp(0), _flags(0), _node(0xffff) {}

    BucketCopy(uint64_t timestamp,
               uint16_t nodeIdx,
               const api::BucketInfo& info) noexcept
        : _timestamp(timestamp),
          _info(info),
          _flags(0),
          _node(nodeIdx)
    {
    }

    bool trusted() const { return _flags & TRUSTED; }

    BucketCopy& setTrusted(bool val = true) {
        if (!val) {
            clearTrusted();
        } else {
            _flags |= TRUSTED;
        }

        return *this;
    }

    void clearTrusted() { _flags &= ~TRUSTED; }

    bool valid() const { return getBucketInfo().valid(); }
    bool empty() const { return getBucketInfo().empty(); }
    bool wasRecentlyCreated() const {
        return (getChecksum() == 1
                && getDocumentCount() == 0
                && getTotalDocumentSize() == 0);
    }

    static BucketCopy recentlyCreatedCopy(uint64_t timestamp, uint16_t nodeIdx)
    {
        return BucketCopy(timestamp, nodeIdx, api::BucketInfo(1, 0, 0, 0, 0));
    }

    uint16_t getNode() const { return _node; }
    uint64_t getTimestamp() const { return _timestamp; }

    uint32_t getChecksum() const { return _info.getChecksum(); }
    uint32_t getDocumentCount() const { return _info.getDocumentCount(); }
    uint32_t getTotalDocumentSize() const
            { return _info.getTotalDocumentSize(); }
    uint32_t getMetaCount() const { return _info.getMetaCount(); }
    uint32_t getUsedFileSize() const { return _info.getUsedFileSize(); }
    bool active() const { return _info.isActive(); }
    bool ready() const { return _info.isReady(); }

    const api::BucketInfo& getBucketInfo() const { return _info; }

    void setBucketInfo(uint64_t timestamp, const api::BucketInfo& bInfo) {
        _info = bInfo;
        _timestamp = timestamp;
    }

    void setActive(bool setactive) {
        _info.setActive(setactive);
    }

    bool consistentWith(const BucketCopy& other,
                        bool countInvalidAsConsistent = false) const
    {
        // If both are valid, check checksum and doc count.
        if (valid() && other.valid()) {
            return (getChecksum() == other.getChecksum()
                    && getDocumentCount() == other.getDocumentCount());
        }

        return countInvalidAsConsistent;
    }

    void print(std::ostream&, bool verbose, const std::string& indent) const;

    std::string toString() const;

    bool operator==(const BucketCopy& other) const {
        return
            getBucketInfo() == other.getBucketInfo() &&
            _flags == other._flags;
    }
};

}

