// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::BucketId
 * \ingroup bucket
 *
 * \brief The document space is divided into buckets, this identifies a chunk.
 *
 * The legacy bucket id internals are:
 *   - A 64 bit internal representation.
 *   - The 6 MSB bits is a number where value 0-58 specifies how many of the
 *     other bits are in use. Values 59+ are invalid.
 *   - The 32 LSB bits are the location. This part may be overridden by
 *     document id schemes to create a first level sorting criteria.
 *   - The remaining 28 bits are GID bits (calculated from MD5), used to split
 *     up buckets with the same location bits.
 *
 * Bucket identifiers are created by the bucket id factory, such that some
 * non-static state can be kept to optimize the generation.
 */

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class nbostream;
    class asciistream;
}

namespace document {

extern const unsigned char reverseBitTable[256];

namespace bucket { class BucketIdList; }

class BucketId
{
public:
    struct hash {
        uint64_t operator () (const BucketId& g) const noexcept;
    };

    /**
     * The primitive type used to store bucket identifiers. If you use the
     * typedef when needed we can alter this later with less code changes.
     */
    using Type = uint64_t;
    using List = bucket::BucketIdList;
    /** Create an initially unset bucket id. */
    constexpr BucketId() noexcept : _id(0) {}
    /** Create a bucket id with the given raw unchecked content. */
    explicit BucketId(Type id) noexcept : _id(id) {}
    /** Create a bucket id using a set of bits from a raw unchecked value. */
    BucketId(uint32_t useBits, Type id) noexcept : _id(createUsedBits(useBits, id)) { }

    bool operator<(const BucketId& id) const noexcept {
        return getId() < id.getId();
    }
    bool operator==(const BucketId& id) const noexcept { return getId() == id.getId(); }
    bool operator!=(const BucketId& id) const noexcept { return getId() != id.getId(); }

    vespalib::string toString() const;

    bool valid() const noexcept {
        return validUsedBits(getUsedBits());
    }

    static bool validUsedBits(uint32_t usedBits) noexcept {
        return (usedBits >= minNumBits) && (usedBits <= maxNumBits);
    }

    bool isSet() const noexcept {
        return _id != 0u;
    }
    /**
     * Create a bucket id that set all unused bits to zero. If you want to
     * verify that two different documents belong to the same bucket given some
     * level of bucket splitting, use this to ignore the unused bits.
     */
    BucketId stripUnused() const noexcept { return BucketId(getUsedBits(), getId());    }

    /**
     * Checks whether the given bucket is contained within this bucket. That is,
     * if it is the same bucket, or if it is a bucket using more bits, which is
     * identical to this one if set to use as many bits as this one.
     */
    bool contains(const BucketId& id) const noexcept;

// Functions exposing internals we want to make users independent of

// private: Setting these private when trying to stop code from using it
    /** Number of MSB bits used to count LSB bits used. */
    enum { CountBits = 6 };

    static constexpr uint32_t maxNumBits = (8 * sizeof(Type) - CountBits);
    static constexpr uint32_t minNumBits = 1u;   // See comment above.

    uint32_t getUsedBits() const noexcept { return _id >> maxNumBits; }

    void setUsedBits(uint32_t used) {
        uint32_t availBits = maxNumBits;
        if (used > availBits) {
            throwFailedSetUsedBits(used, availBits);
        }
        Type usedCount(used);
        usedCount <<= availBits;

        _id <<= CountBits;
        _id >>= CountBits;
        _id |= usedCount;
    }

    /** Get the bucket id value stripped of the bits that are not in use. */
    Type getId() const noexcept { return (_id & getStripMask()); }

    /**
     * Get the bucket id value stripped of the count bits plus the bits that
     * are not in use.
     */
    Type withoutCountBits() const noexcept { return (_id & getUsedMask()); }

    Type getRawId() const noexcept { return _id; }

    /**
     * Reverses the bits in the given number, except the countbits part.
     * Used for sorting in the bucket database as we want related buckets
     * to be sorted next to each other.
     */
    static Type bucketIdToKey(Type id) noexcept {
        Type retVal = reverse(id);

        Type usedCountLSB = id >> maxNumBits;
        retVal >>= CountBits;
        retVal <<= CountBits;
        retVal |= usedCountLSB;

        return retVal;
    }

    static Type keyToBucketId(Type key) noexcept ;

    /**
     * Reverses the bucket id bitwise, except the countbits part,
     * and returns the value,
     */
    Type toKey() const noexcept { return bucketIdToKey(getId()); };

    /**
     * Reverses the order of the bits in the bucket id.
     */
    static Type reverse(Type id) noexcept;

    /**
     * Returns the value of the Nth bit, counted in the reverse order of the
     * bucket id.
     */
    uint8_t getBit(uint32_t n) const noexcept {
        return (_id & ((Type)1 << n)) == 0 ? 0 : 1;
    }

    static void initialize() noexcept;
private:
    static Type _usedMasks[maxNumBits+1];
    static Type _stripMasks[maxNumBits+1];

    Type _id;

    Type getUsedMask() const noexcept {
        return _usedMasks[getUsedBits()];
    }

    Type getStripMask() const noexcept {
        return _stripMasks[getUsedBits()];
    }

    static Type createUsedBits(uint32_t used, Type id) noexcept {
        uint32_t availBits = maxNumBits;
        Type usedCount(used);
        usedCount <<= availBits;

        id <<= CountBits;
        id >>= CountBits;
        id |= usedCount;
        return id;
    }

    static void throwFailedSetUsedBits(uint32_t used, uint32_t availBits);

    friend vespalib::nbostream& operator<<(vespalib::nbostream&, const BucketId&);
    friend vespalib::nbostream& operator>>(vespalib::nbostream&, BucketId&);
};

vespalib::asciistream& operator<<(vespalib::asciistream&, const BucketId&);
std::ostream& operator<<(std::ostream&, const BucketId&);

} // document
