// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <persistence/spi/types.h>

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
    Timestamp _timestamp;

    RawDocumentMetaData(void)
        : _gid(),
          _bucketUsedBits(BucketId::minNumBits),
          _timestamp()
    { }

    RawDocumentMetaData(const GlobalId &gid, const BucketId &bucketId, const Timestamp &timestamp)
        : _gid(gid),
          _bucketUsedBits(bucketId.getUsedBits()),
          _timestamp(timestamp)
    {
        assert(bucketId.valid());
        BucketId verId(gid.convertToBucketId());
        verId.setUsedBits(_bucketUsedBits);
        assert(bucketId.getRawId() == verId.getRawId() ||
               bucketId.getRawId() == verId.getId());
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
        assert(BucketId::validateUsedBits(bucketUsedBits));
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

    Timestamp getTimestamp(void) const { return _timestamp; }

    void setTimestamp(const Timestamp &timestamp) { _timestamp = timestamp; }
};

} // namespace proton

