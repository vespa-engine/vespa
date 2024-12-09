// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_posting_list_cache.h"

namespace search::diskindex {

/*
 * Class for caching posting lists read from disk.
 * It uses an LRU cache from vespalib.
 */
class PostingListCache : public IPostingListCache {
public:
    class BackingStore;
private:
    class Cache;
    class BitVectorCache;
    std::unique_ptr<const BackingStore> _backing_store;
    std::unique_ptr<Cache> _cache;
    std::unique_ptr<BitVectorCache> _bitvector_cache;
public:
    class CacheSizingParams {
        size_t _posting_max_bytes   = 0;
        size_t _bitvector_max_bytes = 0;
        double _posting_slru_protected_ratio   = 0.0; // [0, 1]
        double _bitvector_slru_protected_ratio = 0.0; // [0, 1]
    public:
        constexpr CacheSizingParams() noexcept = default;
        CacheSizingParams(size_t posting_max_bytes, size_t bitvector_max_bytes,
                          double posting_slru_protected_ratio, double bitvector_slru_protected_ratio) noexcept
            : _posting_max_bytes(posting_max_bytes),
              _bitvector_max_bytes(bitvector_max_bytes),
              _posting_slru_protected_ratio(std::min(std::max(posting_slru_protected_ratio, 0.0), 1.0)),
              _bitvector_slru_protected_ratio(std::min(std::max(bitvector_slru_protected_ratio, 0.0), 1.0))
        {
        }

        [[nodiscard]] size_t posting_slru_protected_bytes() const noexcept {
            if (_posting_slru_protected_ratio <= 0) {
                return 0;
            }
            return static_cast<size_t>(static_cast<double>(_posting_max_bytes) * _posting_slru_protected_ratio);
        }
        [[nodiscard]] size_t posting_slru_probationary_bytes() const noexcept {
            return _posting_max_bytes - posting_slru_protected_bytes();
        }

        [[nodiscard]] size_t bitvector_slru_protected_bytes() const noexcept {
            if (_bitvector_slru_protected_ratio <= 0) {
                return 0;
            }
            return static_cast<size_t>(static_cast<double>(_bitvector_max_bytes) * _bitvector_slru_protected_ratio);
        }
        [[nodiscard]] size_t bitvector_slru_probationary_bytes() const noexcept {
            return _bitvector_max_bytes - bitvector_slru_protected_bytes();
        }
    };

    explicit PostingListCache(const CacheSizingParams& params);
    PostingListCache(size_t max_bytes, size_t bitvector_max_bytes);
    ~PostingListCache() override;
    search::index::PostingListHandle read(const Key& key, Context& ctx) const override;
    std::shared_ptr<BitVector> read(const BitVectorKey& key, Context& ctx) const override;
    vespalib::CacheStats get_stats() const override;
    vespalib::CacheStats get_bitvector_stats() const override;
    bool enabled_for_posting_lists() const noexcept override;
    bool enabled_for_bitvectors() const noexcept override;
    static size_t element_size();
    static size_t bitvector_element_size();
};

}
