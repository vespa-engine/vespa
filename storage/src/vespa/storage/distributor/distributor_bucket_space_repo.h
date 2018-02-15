// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <memory>
#include <unordered_map>

namespace storage {

namespace lib { class Distribution; }

namespace distributor {

class DistributorBucketSpace;

class DistributorBucketSpaceRepo {
public:
    using BucketSpaceMap = std::unordered_map<document::BucketSpace, std::unique_ptr<DistributorBucketSpace>, document::BucketSpace::hash>;

private:
    BucketSpaceMap _map;

public:
    DistributorBucketSpaceRepo();
    ~DistributorBucketSpaceRepo();

    DistributorBucketSpaceRepo(const DistributorBucketSpaceRepo&&) = delete;
    DistributorBucketSpaceRepo& operator=(const DistributorBucketSpaceRepo&) = delete;
    DistributorBucketSpaceRepo(DistributorBucketSpaceRepo&&) = delete;
    DistributorBucketSpaceRepo& operator=(DistributorBucketSpaceRepo&&) = delete;

    DistributorBucketSpace &get(document::BucketSpace bucketSpace);
    const DistributorBucketSpace &get(document::BucketSpace bucketSpace) const;

    BucketSpaceMap::const_iterator begin() const { return _map.begin(); }
    BucketSpaceMap::const_iterator end() const { return _map.end(); }
    void add(document::BucketSpace bucketSpace, std::unique_ptr<DistributorBucketSpace> distributorBucketSpace);
};

}
}
