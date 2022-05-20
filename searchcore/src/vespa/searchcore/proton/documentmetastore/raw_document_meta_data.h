// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/types.h>
#include <algorithm>
#include <atomic>
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
    std::atomic<uint32_t> _bucket_used_bits_and_doc_size;
    std::atomic<uint64_t> _timestamp;

    static uint32_t capped_doc_size(uint32_t doc_size) { return std::min(0xffffffu, doc_size); }

    RawDocumentMetaData() noexcept
        : _gid(),
          _bucket_used_bits_and_doc_size(BucketId::minNumBits),
          _timestamp(0)
    { }

    RawDocumentMetaData(const GlobalId &gid, const BucketId &bucketId, const Timestamp &timestamp, uint32_t docSize) noexcept
        : _gid(gid),
          _bucket_used_bits_and_doc_size(bucketId.getUsedBits() | (capped_doc_size(docSize) << 8)),
          _timestamp(timestamp)
    {
        assert(bucketId.valid());
        BucketId verId(gid.convertToBucketId());
        verId.setUsedBits(bucketId.getUsedBits());
        assert(bucketId.getRawId() == verId.getRawId() ||
               bucketId.getRawId() == verId.getId());
    }

    RawDocumentMetaData(const RawDocumentMetaData& rhs)
        : _gid(rhs._gid),
          _bucket_used_bits_and_doc_size(rhs._bucket_used_bits_and_doc_size.load(std::memory_order_relaxed)),
          _timestamp(rhs._timestamp.load(std::memory_order_relaxed))
    {
    }

    RawDocumentMetaData& operator=(const RawDocumentMetaData& rhs) {
        _gid = rhs._gid;
        _bucket_used_bits_and_doc_size.store(rhs._bucket_used_bits_and_doc_size.load(std::memory_order_relaxed), std::memory_order_relaxed);
        _timestamp.store(rhs._timestamp.load(std::memory_order_relaxed), std::memory_order_relaxed);
        return *this;
    }

    bool operator<(const GlobalId &rhs) const noexcept { return _gid < rhs; }
    bool operator==(const GlobalId &rhs) const noexcept { return _gid == rhs; }
    bool operator<(const RawDocumentMetaData &rhs) const noexcept { return _gid < rhs._gid; }
    bool operator==(const RawDocumentMetaData &rhs) const noexcept { return _gid == rhs._gid; }

    const GlobalId &getGid() const { return _gid; }
    GlobalId &getGid() { return _gid; }
    void setGid(const GlobalId &rhs) { _gid = rhs; }
    uint8_t getBucketUsedBits() const { return _bucket_used_bits_and_doc_size.load(std::memory_order_relaxed) & 0xffu; }

    BucketId getBucketId() const {
        BucketId ret(_gid.convertToBucketId());
        ret.setUsedBits(getBucketUsedBits());
        return ret;
    }

    void setBucketUsedBits(uint8_t bucketUsedBits) {
        assert(BucketId::validUsedBits(bucketUsedBits));
        _bucket_used_bits_and_doc_size.store((_bucket_used_bits_and_doc_size.load(std::memory_order_relaxed) & ~0xffu) | bucketUsedBits, std::memory_order_relaxed);
    }

    void setBucketId(const BucketId &bucketId) {
        assert(bucketId.valid());
        uint8_t bucketUsedBits = bucketId.getUsedBits();
        BucketId verId(_gid.convertToBucketId());
        verId.setUsedBits(bucketUsedBits);
        assert(bucketId.getRawId() == verId.getRawId() ||
               bucketId.getRawId() == verId.getId());
        setBucketUsedBits(bucketUsedBits);
    }

    Timestamp getTimestamp() const { return Timestamp(_timestamp.load(std::memory_order_relaxed)); }

    void setTimestamp(const Timestamp &timestamp) { _timestamp.store(timestamp.getValue(), std::memory_order_relaxed); }

    uint32_t getDocSize() const { return _bucket_used_bits_and_doc_size.load(std::memory_order_relaxed) >> 8; }
    void setDocSize(uint32_t docSize) { _bucket_used_bits_and_doc_size.store((_bucket_used_bits_and_doc_size.load(std::memory_order_relaxed) & 0xffu) | (capped_doc_size(docSize) << 8), std::memory_order_relaxed); }

};

} // namespace proton

