// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketcopy.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/time.h>

namespace storage {

namespace distributor {
    class DistributorStripeTestUtil;
    class TopLevelDistributorTestUtil;
}

enum class TrustedUpdate {
    UPDATE,
    DEFER
};

template <typename NodeSeq>
class BucketInfoBase
{
protected:
    //TODO: Should we use a chrono timepoint to ensure we are using same clock everywhere ?
    uint32_t _lastGarbageCollection;
    NodeSeq _nodes;
public:
    BucketInfoBase()
        : _lastGarbageCollection(0),
          _nodes()
    {}
    BucketInfoBase(uint32_t lastGarbageCollection, const NodeSeq& nodes)
        : _lastGarbageCollection(lastGarbageCollection),
          _nodes(nodes)
    {}
    BucketInfoBase(uint32_t lastGarbageCollection, NodeSeq&& nodes)
        : _lastGarbageCollection(lastGarbageCollection),
          _nodes(std::move(nodes))
    {}
    ~BucketInfoBase() = default;

    BucketInfoBase(const BucketInfoBase&) = default;
    BucketInfoBase& operator=(const BucketInfoBase&) = default;
    BucketInfoBase(BucketInfoBase&&) noexcept = default;
    BucketInfoBase& operator=(BucketInfoBase&&) noexcept = default;

    /**
     * @return Returns the last time when this bucket was "garbage collected".
     */
    uint32_t getLastGarbageCollectionTime() const { return _lastGarbageCollection; }

    /** True if the bucket contains no documents and is consistent. */
    bool emptyAndConsistent() const;

    /**
       Check that all copies have complete bucket information and are
       consistent with eachother.
    */
    bool validAndConsistent() const;

    /**
     * True if the bucket contains at least one invalid copy
     */
    bool hasInvalidCopy() const;

    /**
     * Returns the number of trusted nodes this entry has.
     */
    uint16_t getTrustedCount() const;

    bool hasTrusted() const {
        return getTrustedCount() != 0;
    }

    /**
     * Check that all of the nodes have the same checksums.
     *
     * @param countInCompleteAsInconsistent If false, nodes that are incomplete
     *       are always counted as consistent with complete nodes.
     */
    bool consistentNodes(bool countInvalidAsConsistent = false) const;

    void print(std::ostream&, bool verbose, const std::string& indent) const;

    /**
     * Returns the bucket copy struct for the given node, null if nonexisting
     */
    const BucketCopy* getNode(uint16_t node) const;

    /**
     * Returns the number of nodes this entry has.
     */
    uint32_t getNodeCount() const noexcept { return static_cast<uint32_t>(_nodes.size()); }

    /**
     * Returns a list of the nodes this entry has.
     */
    std::vector<uint16_t> getNodes() const;

    /**
       Returns a reference to the node with the given index in the node
       array. This operation has undefined behaviour if the index given
       is not within the node count.
    */
    const BucketCopy& getNodeRef(uint16_t idx) const {
        return _nodes[idx];
    }

    const NodeSeq& getRawNodes() const noexcept {
        return _nodes;
    }

    // If there is a valid majority of replicas that have the same metadata
    // (checksum and document count), return that bucket info.
    // Otherwise, return default-constructed info with valid() == false.
    api::BucketInfo majority_consistent_bucket_info() const noexcept;

    std::string toString() const;

    uint32_t getHighestDocumentCount() const;
    uint32_t getHighestTotalDocumentSize() const;
    uint32_t getHighestMetaCount() const;
    uint32_t getHighestUsedFileSize() const;

    bool hasRecentlyCreatedEmptyCopy() const;

    bool operator==(const BucketInfoBase& other) const;
};

template <typename NodeSeq>
std::ostream& operator<<(std::ostream& out, const BucketInfoBase<NodeSeq>& info) {
    info.print(out, false, "");
    return out;
}

class ConstBucketInfoRef : public BucketInfoBase<vespalib::ConstArrayRef<BucketCopy>> {
public:
    using BucketInfoBase::BucketInfoBase;
};

class BucketInfo : public BucketInfoBase<std::vector<BucketCopy>> {
public:
    BucketInfo();
    BucketInfo(uint32_t lastGarbageCollection, std::vector<BucketCopy> nodes);
    ~BucketInfo();

    BucketInfo(const BucketInfo&);
    BucketInfo& operator=(const BucketInfo&);
    BucketInfo(BucketInfo&&) noexcept;
    BucketInfo& operator=(BucketInfo&&) noexcept;

    /**
     * Sets the last time the bucket was "garbage collected".
     */
    void setLastGarbageCollectionTime(uint32_t timestamp) {
        _lastGarbageCollection = timestamp;
    }

    /**
       Update trusted flags if bucket is now complete and consistent.
    */
    void updateTrusted();

    /**
       Removes any historical information on trustedness, and sets the bucket copies to
       trusted if they are now complete and consistent.
    */
    void resetTrusted();

    /**
       Adds the given node.

       @param recommendedOrder A recommended ordering of nodes.
       All nodes in this list will be ordered first, in the order

       listed. Any nodes not in this list will be order numerically afterward.
       @param replace If replace is true, replaces old ones that may exist.
       @param update If true, will invoke updateTrusted() after replicas are added
    */
    void addNodes(const std::vector<BucketCopy>& newCopies,
                  const std::vector<uint16_t>& recommendedOrder,
                  TrustedUpdate update = TrustedUpdate::UPDATE);

    /**
       Simplified API for the common case of inserting one node. See addNodes().
    */
    void addNode(const BucketCopy& newCopy,
                 const std::vector<uint16_t>& recommendedOrder);

    /**
       Updates bucket information for a node. Does nothing if the node
       doesn't already exist.
    */
    void updateNode(const BucketCopy& newCopy);

    /**
        Returns true if the node existed and was removed.
    */
    bool removeNode(uint16_t node, TrustedUpdate update = TrustedUpdate::UPDATE);

    /**
       Clears all nodes from the bucket information.
    */
    void clear() { _nodes.clear(); }

private:
    friend class distributor::DistributorStripeTestUtil;
    friend class distributor::TopLevelDistributorTestUtil;

    /**
     * Returns the bucket copy struct for the given node, null if nonexisting
     */
    BucketCopy* getNodeInternal(uint16_t node);

    void addNodeManual(const BucketCopy& newCopy) { _nodes.push_back(newCopy); }
};

extern template class BucketInfoBase<std::vector<BucketCopy>>;
extern template class BucketInfoBase<vespalib::ConstArrayRef<BucketCopy>>;

}

