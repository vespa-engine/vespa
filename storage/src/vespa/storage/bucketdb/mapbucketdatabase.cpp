// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapbucketdatabase.h"
#include <vespa/storage/common/bucketoperationlogger.h>

namespace storage {

MapBucketDatabase::MapBucketDatabase()
{
    // Allocate the root element.
    allocate();
}

MapBucketDatabase::~MapBucketDatabase() {}

MapBucketDatabase::E::~E() { }

uint32_t
MapBucketDatabase::allocate()
{
    if (!_free.empty()) {
        uint32_t retVal = _free[_free.size() - 1];
        _free.pop_back();
        return retVal;
    }

    _db.push_back(E());
    return _db.size() - 1;
}

uint32_t
MapBucketDatabase::allocateValue(const document::BucketId& bid)
{
    if (!_freeValues.empty()) {
        uint32_t retVal = _freeValues[_freeValues.size() - 1];
        _freeValues.pop_back();
        return retVal;
    }

    _values.push_back(BucketDatabase::Entry(bid));
    return _values.size() - 1;
}

BucketDatabase::Entry*
MapBucketDatabase::find(int index, uint8_t bitCount,
                        const document::BucketId& bid, bool create)
{
    if (index == -1) {
        return NULL;
    }

    E& e = _db[index];
    if (bitCount == bid.getUsedBits()) {
        if (e.value == -1) {
            if (create) {
                e.value = allocateValue(bid);
            } else {
                return NULL;
            }
        }

        return &_values[e.value];
    }

    // Must reference _db[index] rather than E, since the address of E may change
    // in allocate().
    if (bid.getBit(bitCount) == 0) {
        if (e.e_0 == -1 && create) {
            int val = allocate();
            _db[index].e_0 = val;
        }

        return find(_db[index].e_0, bitCount + 1, bid, create);
    } else {
        if (e.e_1 == -1 && create) {
            int val = allocate();
            _db[index].e_1 = val;
        }

        return find(_db[index].e_1, bitCount + 1, bid, create);
    }
}

BucketDatabase::Entry
MapBucketDatabase::get(const document::BucketId& bucket) const
{
    MapBucketDatabase& mutableSelf(const_cast<MapBucketDatabase&>(*this));
    Entry* found = mutableSelf.find(0, 0, bucket, false);
    if (found) {
        return *found;
    } else {
        return BucketDatabase::Entry();
    }
}

bool
MapBucketDatabase::remove(int index,
                          uint8_t bitCount,
                          const document::BucketId& bid)
{
    if (index == -1) {
        return false;
    }

    E& e = _db[index];
    if (bitCount == bid.getUsedBits()) {
        if (e.value != -1) {
            _freeValues.push_back(e.value);
            e.value = -1;
        }
    }

    if (bid.getBit(bitCount) == 0) {
        if (remove(e.e_0, bitCount + 1, bid)) {
            e.e_0 = -1;
        }
    } else {
        if (remove(e.e_1, bitCount + 1, bid)) {
            e.e_1 = -1;
        }
    }

    if (e.empty() && index > 0) {
        _free.push_back(index);
        return true;
    } else {
        return false;
    }
}

void
MapBucketDatabase::remove(const document::BucketId& bucket)
{
    LOG_BUCKET_OPERATION_NO_LOCK(bucket, "REMOVING from bucket db!");
    remove(0, 0, bucket);
}

void
MapBucketDatabase::update(const Entry& newEntry)
{
    assert(newEntry.valid());
    LOG_BUCKET_OPERATION_NO_LOCK(
            newEntry.getBucketId(),
            vespalib::make_vespa_string(
                    "bucketdb insert of %s", newEntry.toString().c_str()));

    Entry* found = find(0, 0, newEntry.getBucketId(), true);
    assert(found);
    *found = newEntry;
}

void
MapBucketDatabase::findParents(int index,
                               uint8_t bitCount,
                               const document::BucketId& bid,
                               std::vector<Entry>& entries) const
{
    if (index == -1) {
        return;
    }

    const E& e = _db[index];
    if (e.value != -1) {
        entries.push_back(_values[e.value]);
    }

    if (bitCount >= bid.getUsedBits()) {
        return;
    }

    if (bid.getBit(bitCount) == 0) {
        findParents(e.e_0, bitCount + 1, bid, entries);
    } else {
        findParents(e.e_1, bitCount + 1, bid, entries);
    }
}


void
MapBucketDatabase::getParents(const document::BucketId& childBucket,
                              std::vector<Entry>& entries) const
{
    findParents(0, 0, childBucket, entries);
}

void
MapBucketDatabase::findAll(int index,
                           uint8_t bitCount,
                           const document::BucketId& bid,
                           std::vector<Entry>& entries) const
{
    if (index == -1) {
        return;
    }

    const E& e = _db[index];
    if (e.value != -1) {
        entries.push_back(_values[e.value]);
    }

    if (bitCount >= bid.getUsedBits()) {
        findAll(e.e_0, bitCount + 1, bid, entries);
        findAll(e.e_1, bitCount + 1, bid, entries);
    } else {
        if (bid.getBit(bitCount) == 0) {
            findAll(e.e_0, bitCount + 1, bid, entries);
        } else {
            findAll(e.e_1, bitCount + 1, bid, entries);
        }
    }
}

void
MapBucketDatabase::getAll(const document::BucketId& bucket,
                           std::vector<Entry>& entries) const
{
    findAll(0, 0, bucket, entries);
}

/**
 * Any child bucket under a bucket held in an inner node will be ordered after
 * (i.e. be greater than) the inner node bucket. This is because in bucket key
 * order these have the same bit prefix but are guaranteed to have a suffix that
 * make them greater. From our bucket ordering spec, a bucket with 5 bits of
 * 00000 is greater than a bucket of 3 bits of 000 because the suffix logically
 * takes into account the number of used bucket bits (meaning the actual
 * values are more akin to 000000000:5 and 00000000:3). When traversing the bit
 * tree, we mirror this behavior since all child nodes by definition have a
 * higher used bit value from their depth in the tree.
 */
int
MapBucketDatabase::findFirstInOrderNodeInclusive(int index) const
{
    if (index == -1) {
        return -1;
    }

    int follow = index;
    while (true) {
        const E& e = _db[follow];
        if (e.value != -1) {
            return follow;
        }
        // In-order 0 bits sort before 1 bits so we follow the 0 branch if
        // at all possible. It is illegal for a branch to exist without there
        // existing a leaf somewhere underneath it, so we're destined to hit
        // something if it exists.
        follow = (e.e_0 != -1 ? e.e_0 : e.e_1);
        if (follow == -1) {
            return -1;
        }
    }
}

/**
 * Follow the bit tree as far as we can based on upper bound `value`. To get a
 * bucket with an ID greater than `value` we must try to follow the bit tree
 * as far down as possible, taking the branches that correspond to our input
 * value:
 *  1) If input value has a 0 bit in the `depth` position but no such branch
 *     exists at the current node we look in its 1 branch (if one exists),
 *     returning the first in-order child.
 *  2) If we've reached a node that equals the input value (current depth
 *     equals used bits), look for the first in-order child under the node
 *     in question.
 *   3) Otherwise, keep recursing down the same bit prefix subtree.
 */
int
MapBucketDatabase::upperBoundImpl(int index,
                                  uint8_t depth,
                                  const document::BucketId& value) const
{
    if (index == -1) {
        return -1; // Branch with no children; bail out and up.
    }

    const E& e = _db[index];
    if (depth < value.getUsedBits()) {
        if (value.getBit(depth) == 0) {
            int candidate = upperBoundImpl(e.e_0, depth + 1, value);
            if (candidate != -1) {
                return candidate;
            }
            // No choice but to try to follow 1-branch.
            return findFirstInOrderNodeInclusive(e.e_1);
        } else {
            return upperBoundImpl(e.e_1, depth + 1, value);
        }
    } else {
        // We've hit a node whose bucket ID corresponds exactly to that given
        // in `value`. Find the first in-order child node, if one exists.
        // Please see findFirstInOrderNodeInclusive() comments for an
        // explanation of why this satisfies the upper bound ordering
        // requirements.
        // Due to Funky Business(tm) inside BucketId, asking for getBit beyond
        // usedBits returns potentially undefined values, so we have to treat
        // this case by itself.
        int candidate = findFirstInOrderNodeInclusive(e.e_0);
        if (candidate == -1) {
            candidate = findFirstInOrderNodeInclusive(e.e_1);
        }
        return candidate;
    }
}

BucketDatabase::Entry
MapBucketDatabase::upperBound(const document::BucketId& value) const
{
    int index = upperBoundImpl(0, 0, value);
    if (index != -1) {
        assert(_db[index].value != -1);
        return _values[_db[index].value];
    }
    return Entry::createInvalid();
}

template <typename EntryProcessorType>
bool
MapBucketDatabase::forEach(int index,
                           EntryProcessorType& processor,
                           uint8_t bitCount,
                           const document::BucketId& lowerBound,
                           bool& process)
{
    if (index == -1) {
        return true;
    }

    E& e = _db[index];
    if (e.value != -1 && process && !processor.process(_values[e.value])) {
        return false;
    }

    // We have followed the bucket to where we want to start,
    // start processing.
    if (!process && bitCount >= lowerBound.getUsedBits()) {
        process = true;
    }

    if (process || lowerBound.getBit(bitCount) == 0) {
        if (!forEach(e.e_0, processor, bitCount + 1, lowerBound, process)) {
            return false;
        }
    }

    if (process || lowerBound.getBit(bitCount) != 0) {
        if (!forEach(e.e_1, processor, bitCount + 1, lowerBound, process)) {
            return false;
        }
    }

    return true;
}

void
MapBucketDatabase::forEach(EntryProcessor& processor,
                           const document::BucketId& after) const
{
    bool process = false;
    MapBucketDatabase& mutableSelf(const_cast<MapBucketDatabase&>(*this));
    mutableSelf.forEach(0, processor, 0, after, process);
}

void
MapBucketDatabase::forEach(MutableEntryProcessor& processor,
                           const document::BucketId& after)
{
    bool process = false;
    forEach(0, processor, 0, after, process);
}

void
MapBucketDatabase::clear()
{
    _db.clear();
    _values.clear();
    _free.clear();
    _freeValues.clear();
    allocate();
}

uint8_t
MapBucketDatabase::getHighestSplitBit(int index,
                                      uint8_t bitCount,
                                      const document::BucketId& bid,
                                      uint8_t minCount)
{
    if (index == -1) {
        return minCount;
    }

    E& e = _db[index];
    if (bitCount == bid.getUsedBits()) {
        return minCount;
    }

    if (bid.getBit(bitCount) == 0) {
        if (e.e_0 != -1) {
            minCount = getHighestSplitBit(e.e_0,
                                          bitCount + 1,
                                          bid,
                                          minCount);
        }

        if (e.e_1 != -1) {
            return std::max((int)minCount, bitCount + 1);
        }
    } else {
        if (e.e_1 != -1) {
            minCount = getHighestSplitBit(e.e_1,
                                          bitCount + 1,
                                          bid,
                                          minCount);
        }

        if (e.e_0 != -1) {
            return std::max((int)minCount, bitCount + 1);
        }
    }

    return minCount;

}

document::BucketId
MapBucketDatabase::getAppropriateBucket(
        uint16_t minBits,
        const document::BucketId& bid)
{
    return document::BucketId(getHighestSplitBit(0, 0, bid, minBits),
                              bid.getRawId());
}

uint32_t 
MapBucketDatabase::childCountImpl(int index,
                                  uint8_t bitCount,
                                  const document::BucketId& b) const
{
    if (index == -1) {
        // A non-existing node cannot have any subtrees (obviously).
        return 0;
    }
    const E& e(_db[index]);
    if (bitCount == b.getUsedBits()) {
        // If a child has a valid index, it counts as a subtree.
        return ((e.e_0 != -1) + (e.e_1 != -1));
    }
    if (b.getBit(bitCount) == 0) {
        return childCountImpl(e.e_0, bitCount + 1, b);
    } else {
        return childCountImpl(e.e_1, bitCount + 1, b);
    }
}

uint32_t
MapBucketDatabase::childCount(const document::BucketId& b) const
{
    return childCountImpl(0, 0, b);
}


namespace {
    struct Writer : public BucketDatabase::EntryProcessor {
        std::ostream& _ost;
        Writer(std::ostream& ost) : _ost(ost) {}
        bool process(const BucketDatabase::Entry& e) override {
            _ost << e.toString() << "\n";
            return true;
        }
    };
}

void
MapBucketDatabase::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    (void) indent;
    if (verbose) {
        Writer writer(out);
        forEach(writer);
        /* Write out all the gory details to debug
        out << "Entries {";
        for (uint32_t i=0, n=_db.size(); i<n; ++i) {
            out << "\n" << indent << "  " << _db[i].e_0 << "," << _db[i].e_1
                << "," << _db[i].value;
        }
        out << "\n" << indent << "}";
        out << "Free {";
        for (uint32_t i=0, n=_free.size(); i<n; ++i) {
            out << "\n" << indent << "  " << _free[i];
        }
        out << "\n" << indent << "}";
        out << "Entries {";
        for (uint32_t i=0, n=_values.size(); i<n; ++i) {
            out << "\n" << indent << "  " << _values[i];
        }
        out << "\n" << indent << "}";
        out << "Free {";
        for (uint32_t i=0, n=_freeValues.size(); i<n; ++i) {
            out << "\n" << indent << "  " << _freeValues[i];
        }
        out << "\n" << indent << "}";
        */
    } else {
        out << "Size(" << size() << ") Nodes("
            << (_db.size() - _free.size() - 1) << ")";
    }
}

} // storage
