// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/compressor.h>
#include <lz4.h>
#include <vespa/searchlib/common/identifiable.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/objects/serializer.h>
#include <algorithm>
#include <unordered_set>
#include <ostream>

namespace search {

template <int BucketBits, typename HashT> struct NormalSketch;

/**
 * Sketch interface.
 */
template <int BucketBits, typename HashT>
struct Sketch {
    enum { bucketBits = BucketBits };
    using hash_type = HashT;
    using SketchType = Sketch<BucketBits, HashT>;
    using UP = std::unique_ptr<SketchType>;

    static const HashT BUCKET_COUNT = HashT(1) << BucketBits;
    static const HashT BUCKET_MASK = BUCKET_COUNT - 1;

    virtual ~Sketch() = default;

    virtual int aggregate(HashT hash) = 0;

    [[nodiscard]] virtual uint32_t getClassId() const = 0;
    virtual void serialize(vespalib::Serializer &os) const = 0;
    virtual void deserialize(vespalib::Deserializer &is) = 0;

    virtual bool operator==(const SketchType &other) const = 0;
    virtual void print(std::ostream &out) const = 0;
};
template <int BucketBits, typename HashT>
std::ostream &operator<<(std::ostream &o, const Sketch<BucketBits, HashT> &s) {
    o << "[";
    s.print(o);
    return o << " ]";
}


template <typename T>
uint8_t countPrefixZeros(T t) {
    uint8_t count = 0;
    const T FIRST_BIT = T(1) << ((sizeof(T) * 8) - 1);
    while (!((t << count) & FIRST_BIT)) {
        ++count;
    }
    return ++count;
}


/**
 * Sketch containing a set of hashes
 */
template <int BucketBits = 10, typename HashT = uint32_t>
struct SparseSketch : Sketch<BucketBits, HashT> {
    using typename Sketch<BucketBits, HashT>::SketchType;
    enum { classId = IDENTIFIABLE_CLASSID_NS(search, SparseSketch) };

    struct IdentityHash {
        size_t operator()(HashT hash) const noexcept { return hash; }
    };
    std::unordered_set<HashT, IdentityHash> hash_set;

    ~SparseSketch() override;
    [[nodiscard]] size_t getSize() const { return hash_set.size(); }

    int aggregate(HashT hash) override {
        return hash_set.insert(hash).second ? 1 : 0;
    }

    [[nodiscard]] uint32_t getClassId() const override { return classId; }
    void serialize(vespalib::Serializer &os) const override;
    void deserialize(vespalib::Deserializer &is) override;

    bool operator==(const SketchType &other) const override {
        const auto *other_sparse = dynamic_cast<const SparseSketch<BucketBits, HashT> *>(&other);
        if (!other_sparse) {
            return false;
        }
        if (hash_set.size() != other_sparse->hash_set.size()) {
            return false;
        }
        for (auto hash : hash_set) {
            if (other_sparse->hash_set.count(hash) == 0) {
                return false;
            }
        }
        return true;
    }
    bool operator==(const SparseSketch<BucketBits, HashT>& other) const {
        return operator==(static_cast<const SketchType&>(other));
    }

    void print(std::ostream &out) const override {
        out << " (" << hash_set.size() << " elements)";
        for (auto hash : hash_set) {
            out << " 0x" << std::hex;
            out.width(8);
            out.fill('0');
            out << hash;
        }
    }

    void merge(const SparseSketch<BucketBits, HashT> &other) {
        hash_set.insert(other.hash_set.begin(), other.hash_set.end());
    }
};


/**
 * Sketch containing a fixed number of buckets
 */
template <int BucketBits = 10, typename HashT = uint32_t>
struct NormalSketch : Sketch<BucketBits, HashT> {
    using typename Sketch<BucketBits, HashT>::SketchType;
    using Sketch<BucketBits, HashT>::BUCKET_COUNT;
    using Sketch<BucketBits, HashT>::BUCKET_MASK;
    using UP = std::unique_ptr<NormalSketch>;
    enum { classId = IDENTIFIABLE_CLASSID_NS(search, NormalSketch) };

    uint8_t bucket[BUCKET_COUNT];

    NormalSketch() { memset(&bucket[0], 0, BUCKET_COUNT); }

    int aggregate(HashT hash) override {
        uint8_t existing_value = bucket[hash & BUCKET_MASK];
        uint8_t new_value = countPrefixZeros(hash | BUCKET_MASK);
        if (new_value > existing_value) {
            bucket[hash & BUCKET_MASK] = new_value;
            return new_value - existing_value;
        }
        return 0;
    }

    uint32_t compress_buckets_into(char *buffer, uint32_t size) const;
    void decompress_buckets_from(char *buffer, uint32_t size);
    [[nodiscard]] uint32_t getClassId() const override { return classId; }
    void serialize(vespalib::Serializer &os) const override;
    void deserialize(vespalib::Deserializer &is) override;

    bool operator==(const SketchType &other) const override {
        const auto *other_normal = dynamic_cast<const NormalSketch<BucketBits, HashT> *>(&other);
        if (!other_normal) {
            return false;
        }
        for (size_t i = 0; i < BUCKET_COUNT; ++i) {
            if (other_normal->bucket[i] != bucket[i]) {
                return false;
            }
        }
        return true;
    }
    bool operator==(const NormalSketch<BucketBits, HashT>& other) const {
        return operator==(static_cast<const SketchType&>(other));
    }

    void print(std::ostream &out) const override {
        for (size_t i = 0; i < BUCKET_COUNT; ++i) {
            out << " " << int(bucket[i]);
        }
    }

    void merge(const NormalSketch<BucketBits, HashT> &other) {
        std::transform(bucket, bucket + BUCKET_COUNT, other.bucket, bucket,
                       [](uint8_t a, uint8_t b) { return std::max(a, b); });
    }

    void merge(const SparseSketch<BucketBits, HashT> &other) {
        for (auto hash : other.hash_set) {
            aggregate(hash);
        }
    }
};

template <int BucketBits, typename HashT>
SparseSketch<BucketBits, HashT>::~SparseSketch() = default;

template <int BucketBits, typename HashT>
void SparseSketch<BucketBits, HashT>::
serialize(vespalib::Serializer &os) const {
    uint32_t size = hash_set.size();
    os << size;
    for (HashT hash : hash_set) {
        os << hash;
    }
}
template <int BucketBits, typename HashT>
void SparseSketch<BucketBits, HashT>::
deserialize(vespalib::Deserializer &is) {
    uint32_t size;
    is >> size;
    for (uint32_t i = 0; i < size; ++i) {
        uint32_t hash;
        is >> hash;
        aggregate(hash);
    }
}

template <int BucketBits, typename HashT>
uint32_t NormalSketch<BucketBits, HashT>::
compress_buckets_into(char *buffer, uint32_t size) const {
    vespalib::compression::CompressionConfig config(vespalib::compression::CompressionConfig::LZ4, 9, 9);
    vespalib::ConstBufferRef org(&bucket[0], BUCKET_COUNT);
    vespalib::DataBuffer compress_buffer(buffer, size);
    vespalib::compression::CompressionConfig::Type r =
        vespalib::compression::compress(config, org, compress_buffer, false);
    assert(compress_buffer.getDead() == buffer);
    if (r == vespalib::compression::CompressionConfig::LZ4) {
        assert(compress_buffer.getDataLen() < BUCKET_COUNT);
        return compress_buffer.getDataLen();
    } else {
        assert(BUCKET_COUNT <= size);
        memcpy(buffer, bucket, BUCKET_COUNT);
        return BUCKET_COUNT;
    }
}
template <int BucketBits, typename HashT>
void NormalSketch<BucketBits, HashT>::
decompress_buckets_from(char *buffer, uint32_t size) {
    if (size == BUCKET_COUNT) {  // not compressed
        memcpy(bucket, buffer, BUCKET_COUNT);
    } else {
        vespalib::ConstBufferRef compressed(buffer, size);
        vespalib::DataBuffer uncompressed(reinterpret_cast<char *>(&bucket[0]), BUCKET_COUNT);
        vespalib::compression::decompress(vespalib::compression::CompressionConfig::LZ4, BUCKET_COUNT,
                                          compressed, uncompressed, false);
    }
}
template <int BucketBits, typename HashT>
void NormalSketch<BucketBits, HashT>::
serialize(vespalib::Serializer &os) const {
    vespalib::alloc::Alloc backing(vespalib::alloc::Alloc::alloc(LZ4_compressBound(BUCKET_COUNT)));
    char * compress_array(static_cast<char *>(backing.get()));
    uint32_t size = compress_buckets_into(compress_array, backing.size());
    os << BUCKET_COUNT << size;
    for (size_t i = 0; i < size; ++i) {
        os << static_cast<uint8_t>(compress_array[i]);
    }
}
template <int BucketBits, typename HashT>
void NormalSketch<BucketBits, HashT>::
deserialize(vespalib::Deserializer &is) {
    uint32_t bucket_count, size;
    is >> bucket_count >> size;
    assert(bucket_count == BUCKET_COUNT);
    uint8_t compressed_array[BUCKET_COUNT];
    for (size_t i = 0; i < size; ++i) {
        is >> compressed_array[i];
    }
    decompress_buckets_from(reinterpret_cast<char *>(compressed_array), size);
}

}  // namespace search
