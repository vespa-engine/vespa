// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationtargetresolver.h"
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/vdslib/distribution/idealnodecalculator.h>
#include <algorithm>

namespace storage::distributor {

class DistributorBucketSpace;

struct BucketInstance : public vespalib::AsciiPrintable {
    document::BucketId _bucket;
    api::BucketInfo    _info;
    lib::Node          _node;
    uint16_t           _idealLocationPriority;
    bool               _trusted;
    bool               _exist;

    BucketInstance() : _idealLocationPriority(0xffff),
                       _trusted(false), _exist(false) {}
    BucketInstance(const document::BucketId& id, const api::BucketInfo& info,
                   lib::Node node, uint16_t idealLocationPriority, bool trusted,
                   bool exist = true);

    void print(vespalib::asciistream& out, const PrintProperties&) const override;
};

class BucketInstanceList : public vespalib::AsciiPrintable {
    std::vector<BucketInstance> _instances;

    /**
     * Resolve and return the least specific bucket in the subtree of (and
     * including) candidateId that is a leaf node in the tree. I.e. a bucket
     * whose insertion will not cause an inconsistency with other leaf buckets
     * in the tree at the minimum possible depth at or below candidateId.
     *
     * Preconditions:
     *   candidateId.contains(mostSpecificId)
     * Postconditions:
     *   <return value>.contains(mostSpecificId)
     */
    document::BucketId leastSpecificLeafBucketInSubtree(
            const document::BucketId& candidateId,
            const document::BucketId& mostSpecificId,
            const BucketDatabase& db) const;

public:
    void add(const BucketInstance& instance) { _instances.push_back(instance); }
    bool contains(lib::Node node) const;
    void removeNodeDuplicates();
    void limitToRedundancyCopies(uint16_t redundancy);
    /**
     * Preconditions:
     *   targetIfNonPreExisting.contains(mostSpecificId)
     * Postconditions:
     *   _instances.size() >= configured redundancy level, unless insufficient
     *   number of nodes are available
     */
    void extendToEnoughCopies(const DistributorBucketSpace& distributor_bucket_space,
                              const BucketDatabase& db,
                              const document::BucketId& targetIfNonPreExisting,
                              const document::BucketId& mostSpecificId);

    void populate(const document::BucketId&, const DistributorBucketSpace&, BucketDatabase&);
    void add(BucketDatabase::Entry& e, const lib::IdealNodeList& idealState);

    template <typename Order>
    void sort(const Order& order) {
        std::sort(_instances.begin(), _instances.end(), order);
    }

    OperationTargetList createTargets(document::BucketSpace bucketSpace);

    void print(vespalib::asciistream& out, const PrintProperties& p) const override;
};

class OperationTargetResolverImpl : public OperationTargetResolver {
    const DistributorBucketSpace& _distributor_bucket_space;
    BucketDatabase& _bucketDatabase;
    uint32_t _minUsedBucketBits;
    uint16_t _redundancy;
    document::BucketSpace _bucketSpace;

public:
    OperationTargetResolverImpl(const DistributorBucketSpace& distributor_bucket_space,
                                BucketDatabase& bucketDatabase,
                                uint32_t minUsedBucketBits,
                                uint16_t redundancy,
                                document::BucketSpace bucketSpace)
        : _distributor_bucket_space(distributor_bucket_space),
          _bucketDatabase(bucketDatabase),
          _minUsedBucketBits(minUsedBucketBits),
          _redundancy(redundancy),
          _bucketSpace(bucketSpace)
    {}

    BucketInstanceList getAllInstances(OperationType type,
                                       const document::BucketId& id);
    BucketInstanceList getInstances(OperationType type, const document::BucketId& id) {
        BucketInstanceList result(getAllInstances(type, id));
        result.limitToRedundancyCopies(_redundancy);
        return result;
    }

    OperationTargetList getTargets(OperationType type, const document::BucketId& id) override {
        return getInstances(type, id).createTargets(_bucketSpace);
    }
};

}
