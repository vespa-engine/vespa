// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::GlobalId
 * \ingroup base
 *
 * \brief Representation of a global ID.
 *
 * The global ID, is a hash of the document ID, used where we need to
 * distinguish between documents, but where storing a variable length string is
 * not practical. VESPA search currently assumes that there will be no hash
 * collisions, and if one should occur, the latest document will be the only
 * one present in the indexes. VESPA document storage handles global ID
 * collisions, but optimize code for the instances where it doesn't happen.
 *
 * It's a 96 bit MD5 checksum, so the chances of a collision is very very small.
 *
 * This class should not inherit from anything, as clients may use it's memory
 * representation directly to save it.
 *
 * The interface for creating or modifying these objects is not user friendly.
 * You should create it by calling DocumentId::getGlobalId() and you should not
 * need to modify it, unless by standard copy constructor or assignment
 * operator.
 */
#pragma once

#include <cstdint>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/bucket/bucketid.h>

namespace document {

class GlobalId {
public:
    /**
     * The number of bytes used to represent a global id.
     */
    static const unsigned int LENGTH = 12;

    /** Hash function that can be used to put global ids in hash set/maps. */
    struct hash {
        size_t operator () (const GlobalId & g) const {
            return g._gid._bucketId._gid;
        }
    };

private:
    struct BucketIdS {
        uint32_t _location;
        uint64_t _gid;
    } __attribute__((packed));
    union {
        unsigned char _buffer[LENGTH];
        BucketIdS     _bucketId;
        uint32_t      _nums[LENGTH/sizeof(uint32_t)];
    } _gid;

public:
    /**
     * Defines a comparator object that can be used for sorting global ids based on bucket order. An std::map
     * using this comparator to order gid keys can use
     * map.lower_bound(GlobalId::calculateFirstInBucket(bucket)) and
     * map.upper_bound(GlobalId::calculateLastInBucket(bucket)) to traverse only those gids that belong to the
     * given bucket.
     */
    struct BucketOrderCmp {
        bool operator()(const GlobalId &lhs, const GlobalId &rhs) const {
            const uint32_t * __restrict__ a = lhs._gid._nums;
            const uint32_t * __restrict__ b = rhs._gid._nums;
            if (a[0] != b[0]) {
                return bitswap(a[0]) < bitswap(b[0]);
            }
            if (a[2] != b[2]) {
                return bitswap(a[2]) < bitswap(b[2]);
            }
            return __builtin_bswap32(a[1]) < __builtin_bswap32(b[1]);
        }
        static uint32_t bitswap(uint32_t value) {
            value = ((value & 0x55555555) << 1) | ((value & 0xaaaaaaaa) >> 1);
            value = ((value & 0x33333333) << 2) | ((value & 0xcccccccc) >> 2);
            value = ((value & 0x0f0f0f0f) << 4) | ((value & 0xf0f0f0f0) >> 4);
            return __builtin_bswap32(value);
        }
        // Return most significant 32 bits of gid key
        static uint32_t gid_key32(const GlobalId &gid) {
            return bitswap(gid._gid._nums[0]);
        }

        //These 2 compare methods are exposed only for testing
        static int compareRaw(unsigned char a, unsigned char b) {
            return a - b;
        }
        static int compare(unsigned char a, unsigned char b) {
            return compareRaw(document::reverseBitTable[a], document::reverseBitTable[b]);
        }
    };

    /**
     * Constructs a new global id with all 0 bits.
     */
    GlobalId() noexcept { set("\0\0\0\0\0\0\0\0\0\0\0\0"); }



    /**
     * Constructs a new global id with initial content. This copies the first LENGTH bytes from the given
     * address into the internal buffer.
     *
     * @param gid The address to the data to copy.
     */
    explicit GlobalId(const void *gid) noexcept { set(gid); }

    GlobalId(const GlobalId &rhs) noexcept = default;

    /**
     * Implements the assignment operator.
     *
     * @param other The global id whose value to copy to this.
     * @return This.
     */
    GlobalId& operator=(const GlobalId& other) noexcept { memcpy(_gid._buffer, other._gid._buffer, sizeof(_gid._buffer)); return *this; }

    /**
     * Implements the equality operator.
     *
     * @param other The global id to compare to.
     * @return True if this equals the other, false otherwise.
     */
    bool operator==(const GlobalId& other) const noexcept { return (memcmp(_gid._buffer, other._gid._buffer, sizeof(_gid._buffer)) == 0); }

    /**
     * Implements the inequality operator.
     *
     * @param other The global id to compare to.
     * @return True if this does NOT equal the other, false otherwise.
     */
    bool operator!=(const GlobalId& other) const noexcept { return (memcmp(_gid._buffer, other._gid._buffer, sizeof(_gid._buffer)) != 0); }

    /**
     * Implements the less-than operator. If you intend to map global ids in such a way that they can
     * efficiently be looked up based on corresponding bucket ids, you should use the {@link BucketOrderCmp}
     * for ordering your collection.
     *
     * @param other The global id to compare to.
     * @return True if comparing the bits of this to the other yields a negative result.
     */
    bool operator<(const GlobalId& other) const noexcept { return (memcmp(_gid._buffer, other._gid._buffer, sizeof(_gid._buffer)) < 0); }

    /**
     * Copies the first LENGTH bytes from the given address into the internal byte array.
     *
     * @param id The bytes to set.
     */
    void set(const void *id) noexcept { memcpy(_gid._buffer, id, sizeof(_gid._buffer)); }

    /**
     * Returns the raw byte array that constitutes this global id.
     */
    const unsigned char *get() const { return _gid._buffer; }

    /**
     * If a GID has been generated from a document ID with a location (n=, g=),
     * the returned value will be deterministic based on the location, and two
     * different document IDs with the same location will return the same value.
     * If not, the value will not have any usable semantics but will still be
     * deterministic in the sense that two identical document IDs will generate
     * the same returned value.
     */
    uint32_t getLocationSpecificBits() const noexcept {
        return _gid._bucketId._location;
    }

    /**
     * Returns a string representation of this global id.
     */
    vespalib::string toString() const;

    /**
     * Parse the source string to generate a global id object.  The source is expected to contain exactly what
     * toString() creates from a global identifier.
     *
     * @param str The string to parse.
     * @throws vespalib::IllegalArgumentException Thrown if input is not in GID format.
     */
    static GlobalId parse(vespalib::stringref str);

    /**
     * Returns the most specified bucket id to which this global id belongs.
     *
     * This function should not be used as it puts too strict limitations on
     * what the bucket identifier can be.. Simplifying implementation to not
     * use bucket id internals. Function should be removed before we can change
     * bucket id.
     *
     * @return The bucket id.
     */
    BucketId convertToBucketId() const {
        uint64_t location(_gid._bucketId._location);
        uint64_t gid(_gid._bucketId._gid);
        return BucketId(58, (gid & 0xFFFFFFFF00000000ull)
                            | (location & 0xFFFFFFFF));
    }

    /**
     * Returns whether or not this global id is contained in the given bucket.
     *
     * @param bucket The bucket to check.
     * @return True, if this gid is contained in the bucket.
     */
    bool containedInBucket(const BucketId &bucket) const;

    /**
     * Given a list of global identifiers sorted in bucket order (see {@link BucketOrderCmp}), this function
     * returns the global id that is the smallest that can exist in the given bucket.
     *
     * @param bucket The bucket id whose lowest gid to find.
     * @return The first global id of the bucket.
     */
    static GlobalId calculateFirstInBucket(const BucketId &bucket);

    /**
     * Given a list of global identifiers sorted in bucket order (see {@link BucketOrderCmp}), this function
     * returns the global id that is the largest that can exist in the given bucket.
     *
     * @param bucket The bucket id whose largest gid to find.
     * @return The last global id of the bucket.
     */
    static GlobalId calculateLastInBucket(const BucketId &bucket);
};

vespalib::asciistream & operator << (vespalib::asciistream & os, const GlobalId & gid);
std::ostream& operator<<(std::ostream& out, const GlobalId& gid);

} // document

