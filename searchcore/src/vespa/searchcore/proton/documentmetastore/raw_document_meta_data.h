// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <persistence/spi/types.h>
#include <cassert>

namespace proton {

/**
 * The raw data that is stored for a single document in the DocumentMetaStore.
 */
struct RawDocumentMetaData
{
    using GlobalId = document::GlobalId;
    using BucketId = document::BucketId;
    using Timestamp = storage::spi::Timestamp;
    GlobalId  _gid;
    uint8_t   _bucketUsedBits;
    uint8_t   _docSizeLow;
    uint16_t  _docSizeHigh;
    Timestamp _timestamp;

    RawDocumentMetaData()
        : _gid(),
          _bucketUsedBits(BucketId::minNumBits),
          _docSizeLow(0),
          _docSizeHigh(0),
          _timestamp()
    { }

    RawDocumentMetaData(const GlobalId &gid, const BucketId &bucketId, const Timestamp &timestamp, uint32_t docSize)
        : _gid(gid),
          _bucketUsedBits(bucketId.getUsedBits()),
          _docSizeLow(docSize),
          _docSizeHigh(docSize >> 8),
          _timestamp(timestamp)
    {
        assert(bucketId.valid());
        BucketId verId(gid.convertToBucketId());
        verId.setUsedBits(_bucketUsedBits);
        assert(bucketId.getRawId() == verId.getRawId() ||
               bucketId.getRawId() == verId.getId());
        if (docSize >= (1u << 24)) {
            _docSizeLow = 0xff;
            _docSizeHigh = 0xffff;
        }
    }

    bool operator<(const GlobalId &rhs) const { return _gid < rhs; }
    bool operator==(const GlobalId &rhs) const { return _gid == rhs; }
    bool operator<(const RawDocumentMetaData &rhs) const { return _gid < rhs._gid; }
    bool operator==(const RawDocumentMetaData &rhs) const { return _gid == rhs._gid; }

    const GlobalId &getGid() const { return _gid; }
    GlobalId &getGid() { return _gid; }
    void setGid(const GlobalId &rhs) { _gid = rhs; }
    uint8_t getBucketUsedBits() const { return _bucketUsedBits; }

    BucketId getBucketId() const {
        BucketId ret(_gid.convertToBucketId());
        ret.setUsedBits(_bucketUsedBits);
        return ret;
    }

    void setBucketUsedBits(uint8_t bucketUsedBits) {
        assert(BucketId::validUsedBits(bucketUsedBits));
        _bucketUsedBits = bucketUsedBits;
    }

    void setBucketId(const BucketId &bucketId) {
        assert(bucketId.valid());
        uint8_t bucketUsedBits = bucketId.getUsedBits();
        BucketId verId(_gid.convertToBucketId());
        verId.setUsedBits(bucketUsedBits);
        assert(bucketId.getRawId() == verId.getRawId() ||
               bucketId.getRawId() == verId.getId());
        _bucketUsedBits = bucketUsedBits;
    }

    Timestamp getTimestamp() const { return _timestamp; }

    void setTimestamp(const Timestamp &timestamp) { _timestamp = timestamp; }

    uint32_t getDocSize() const { return _docSizeLow + (static_cast<uint32_t>(_docSizeHigh) << 8); }
    void setDocSize(uint32_t docSize) { _docSizeLow = docSize; _docSizeHigh = docSize >> 8; }
};

} // namespace proton

