// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    explicit ContentBucketSpaceRepo(const ContentBucketDbOptions&);
    ContentBucketSpace &get(document::BucketSpace bucketSpace) const;
    BucketSpaceMap::const_iterator begin() const noexcept { return _map.begin(); }
    BucketSpaceMap::const_iterator end() const noexcept { return _map.end(); }

    BucketSpaces getBucketSpaces() const;
    size_t getBucketMemoryUsage() const;

    template <typename Functor>
    void for_each_bucket(Functor functor) const {
        for (const auto& elem : _map) {
            elem.second->bucketDatabase().acquire_read_guard()->for_each(functor);
        }
    }

};

}
