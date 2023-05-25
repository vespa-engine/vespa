// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <shared_mutex>
#include <atomic>

namespace search { class BitVector; }
namespace search::attribute {

/**
 * Class that caches posting lists (as bit vectors) for a set of search terms.
 *
 * Lifetime of cached bit vectors is controlled by calling clear() at regular intervals.
 */
class BitVectorSearchCache {
public:
    using BitVectorSP = std::shared_ptr<BitVector>;
    using ReadGuardSP = IDocumentMetaStoreContext::IReadGuard::SP;

    struct Entry {
        // We need to keep a document meta store read guard to ensure that no lids that are cached
        // in the bit vector are re-used until the guard is released.
        ReadGuardSP dmsReadGuard;
        BitVectorSP bitVector;
        uint32_t docIdLimit;
        Entry(ReadGuardSP dmsReadGuard_, BitVectorSP bitVector_, uint32_t docIdLimit_) noexcept
            : dmsReadGuard(std::move(dmsReadGuard_)), bitVector(std::move(bitVector_)), docIdLimit(docIdLimit_) {}
    };

private:
    using Cache = vespalib::hash_map<vespalib::string, std::shared_ptr<Entry>>;

    mutable std::shared_mutex _mutex;
    std::atomic<uint64_t>     _size;
    Cache _cache;

public:
    BitVectorSearchCache();
    ~BitVectorSearchCache();
    void insert(const vespalib::string &term, std::shared_ptr<Entry> entry);
    std::shared_ptr<Entry> find(const vespalib::string &term) const;
    size_t size() const { return _size.load(std::memory_order_relaxed); }
    void clear();
};

}
