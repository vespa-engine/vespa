// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sketch.h"
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/serializer.h>
#include <vespa/vespalib/util/buffer.h>
#include <algorithm>

namespace search {

// How many elements are required before we use a normal sketch representation.
const uint32_t SPARSE_SKETCH_LIMIT = 255;

/**
 * Decorator to SparseSketch handling the switch to NormalSketch
 * representation. It holds a reference to HyperLogLog::_sketch, which
 * is a unique pointer initially pointing to this class. By resetting
 * that pointer to a new sketch class, this class is deleted. By
 * having the logic for exchanging the sketch class here, we remove it
 * along with the sparse representation once the switch is made.
 */
template <int BucketBits = 10, typename HashT = uint32_t>
class ExchangerSketch : public SparseSketch<BucketBits, HashT> {
    typename Sketch<BucketBits, HashT>::UP &_sketch_ptr;

    int aggregate(HashT hash) override {
        if (this->getSize() > SPARSE_SKETCH_LIMIT) {
            NormalSketch<BucketBits, HashT> *normal_sketch =
                new NormalSketch<BucketBits, HashT>;
            normal_sketch->merge(*this);
            _sketch_ptr.reset(normal_sketch);  // deletes this
            return normal_sketch->aggregate(hash);
        }
        return SparseSketch<BucketBits, HashT>::aggregate(hash);
    }
public:
    ExchangerSketch(typename Sketch<BucketBits, HashT>::UP &sketch_ptr)
        : _sketch_ptr(sketch_ptr) {}
};

/**
 * HyperLogLog is used to estimate the number of unique hashes seen.
 */
template <int BucketBits = 10, typename HashT = uint32_t>
class HyperLogLog {
    typename Sketch<BucketBits, HashT>::UP _sketch;

public:
    using hash_type = HashT;
    enum { bucketBits = BucketBits };

    // Initialize ExchangerSketch with a reference to _sketch.
    HyperLogLog() : _sketch(new ExchangerSketch<BucketBits, HashT>(_sketch)) {}
    HyperLogLog(const HyperLogLog<BucketBits, HashT> &other)
        : HyperLogLog() {
        merge(other);
    }
    HyperLogLog<BucketBits, HashT> &operator=(
            const HyperLogLog<BucketBits, HashT> &other) {
        _sketch.reset(new ExchangerSketch<BucketBits, HashT>(_sketch));
        merge(other);
        return *this;
    }

    // Aggregates a hash value into the sketch.
    int aggregate(HashT hash) { return _sketch->aggregate(hash); }
    void merge(const HyperLogLog<BucketBits, HashT> &other);
    void serialize(vespalib::Serializer &os) const;
    void deserialize(vespalib::Deserializer &is);

    const Sketch<BucketBits, HashT> &getSketch() const { return *_sketch; }
};


template <int BucketBits, typename HashT>
void HyperLogLog<BucketBits, HashT>::
merge(const HyperLogLog<BucketBits, HashT> &other) {
    using Sparse = SparseSketch<BucketBits, HashT>;
    using Normal = NormalSketch<BucketBits, HashT>;

    if (_sketch->getClassId() == Sparse::classId) {
        Sparse &sparse = static_cast<Sparse &>(*_sketch);
        if (other.getSketch().getClassId() == Sparse::classId) {
            const Sparse &other_sparse =
                static_cast<const Sparse &>(other.getSketch());
            sparse.merge(other_sparse);
            if (sparse.getSize() > SPARSE_SKETCH_LIMIT) {
                typename Normal::UP new_sketch(new Normal);
                new_sketch->merge(sparse);
                _sketch.reset(new_sketch.release());
            }
        } else {  // other is NormalSketch
            const Normal &other_normal =
                static_cast<const Normal &>(other.getSketch());
            typename Normal::UP new_sketch(new Normal(other_normal));
            new_sketch->merge(sparse);
            _sketch.reset(new_sketch.release());
        }
    } else {  // NormalSketch
        Normal &normal = static_cast<Normal &>(*_sketch);
        if (other.getSketch().getClassId() == Sparse::classId) {
            const Sparse &other_sparse =
                static_cast<const Sparse &>(other.getSketch());
            normal.merge(other_sparse);
        } else {  // other is NormalSketch
            const Normal &other_normal =
                static_cast<const Normal &>(other.getSketch());
            normal.merge(other_normal);
        }
    }
}

template <int BucketBits, typename HashT>
void HyperLogLog<BucketBits, HashT>::
serialize(vespalib::Serializer &os) const {
    os << _sketch->getClassId();
    _sketch->serialize(os);
}

template <int BucketBits, typename HashT>
void HyperLogLog<BucketBits, HashT>::
deserialize(vespalib::Deserializer &is) {
    uint32_t type;
    is >> type;
    if (type == SparseSketch<BucketBits, HashT>::classId) {
        _sketch.reset(new ExchangerSketch<BucketBits, HashT>(_sketch));
        _sketch->deserialize(is);
    } else if (type == NormalSketch<BucketBits, HashT>::classId) {
        _sketch.reset(new NormalSketch<BucketBits, HashT>);
        _sketch->deserialize(is);
    }
}

}  // namespace search

