// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <mutex>

namespace search {

class BitVector;

namespace attribute {

/**
 * Class that caches posting lists (as bit vectors) for a set of search terms.
 *
 * Lifetime of cached bit vectors is controlled by calling clear() at regular intervals.
 */
class BitVectorSearchCache {
public:
    using BitVectorSP = std::shared_ptr<BitVector>;

private:
    using LockGuard = std::lock_guard<std::mutex>;
    using Cache = vespalib::hash_map<vespalib::string, BitVectorSP>;

    mutable std::mutex _mutex;
    Cache _cache;

public:
    BitVectorSearchCache();
    ~BitVectorSearchCache();
    void insert(const vespalib::string &term, BitVectorSP bitVector);
    BitVectorSP find(const vespalib::string &term) const;
    size_t size() const;
    void clear();
};

}
}
