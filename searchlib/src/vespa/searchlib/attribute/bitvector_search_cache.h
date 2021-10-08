// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <mutex>

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
    using ReadGuardUP = IDocumentMetaStoreContext::IReadGuard::UP;

    struct Entry {
        using SP = std::shared_ptr<Entry>;
        // We need to keep a document meta store read guard to ensure that no lids that are cached
        // in the bit vector are re-used until the guard is released.
        ReadGuardUP dmsReadGuard;
        BitVectorSP bitVector;
        uint32_t docIdLimit;
        Entry(ReadGuardUP dmsReadGuard_, BitVectorSP bitVector_, uint32_t docIdLimit_) noexcept
            : dmsReadGuard(std::move(dmsReadGuard_)), bitVector(std::move(bitVector_)), docIdLimit(docIdLimit_) {}
    };

private:
    using LockGuard = std::lock_guard<std::mutex>;
    using Cache = vespalib::hash_map<vespalib::string, Entry::SP>;

    mutable std::mutex _mutex;
    Cache _cache;

public:
    BitVectorSearchCache();
    ~BitVectorSearchCache();
    void insert(const vespalib::string &term, Entry::SP entry);
    Entry::SP find(const vespalib::string &term) const;
    size_t size() const;
    void clear();
};

}
