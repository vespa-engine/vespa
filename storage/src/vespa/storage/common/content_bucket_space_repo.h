// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "content_bucket_space.h"
#include <vespa/document/bucket/bucketspace.h>
#include <unordered_map>

namespace storage {

/**
 * Class managing the set of bucket spaces (with associated bucket databases) on a content node.
 */
class ContentBucketSpaceRepo {
public:
    using BucketSpaceMap = std::unordered_map<document::BucketSpace, ContentBucketSpace::UP, document::BucketSpace::hash>;
    using BucketSpaces = std::vector<document::BucketSpace>;

private:
    BucketSpaceMap _map;

public:
    ContentBucketSpaceRepo();
    ContentBucketSpace &get(document::BucketSpace bucketSpace) const;
    BucketSpaceMap::const_iterator begin() const { return _map.begin(); }
    BucketSpaceMap::const_iterator end() const { return _map.end(); }

    void enableGlobalBucketSpace();

    BucketSpaces getBucketSpaces() const;
    size_t getBucketMemoryUsage() const;

    template <typename Functor>
    void forEachBucket(Functor &functor,
                       const char *clientId) const {
        for (const auto &elem : _map) {
            elem.second->bucketDatabase().all(functor, clientId);
        }
    }

    template <typename Functor>
    void forEachBucketChunked(Functor &functor,
                              const char *clientId) const {
        for (const auto &elem : _map) {
            elem.second->bucketDatabase().chunkedAll(functor, clientId);
        }
    }

};

}
