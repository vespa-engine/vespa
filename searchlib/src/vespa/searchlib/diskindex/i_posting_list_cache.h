// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/bitvector_dictionary_lookup_result.h>
#include <vespa/searchlib/index/postinglisthandle.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <bit>

namespace search { class BitVector; }

namespace search::diskindex {

/*
 * Interface class for caching posting lists read from disk.
 */
class IPostingListCache {
public:
    class IPostingListFileBacking;
    struct Key {
       uint64_t file_id;
       uint64_t bit_offset;
       uint64_t bit_length;
       Key() noexcept : file_id(0), bit_offset(0), bit_length(0) { }
       size_t hash() const noexcept { return std::rotl(file_id, 40) + bit_offset; }
       bool operator==(const Key& rhs) const noexcept {
           // Don't check backing_store_file, it is just passed in key for convenience
           return file_id == rhs.file_id &&
                  bit_offset == rhs.bit_offset &&
                  bit_length == rhs.bit_length;
       }
    };
    struct BitVectorKey {
        uint64_t                               file_id;
        index::BitVectorDictionaryLookupResult lookup_result;
        BitVectorKey() noexcept : file_id(0), lookup_result() { }
        size_t hash() const noexcept { return std::rotl(file_id, 40) + lookup_result.idx; }
        bool operator==(const BitVectorKey& rhs) const noexcept {
            return file_id == rhs.file_id && lookup_result.idx == rhs.lookup_result.idx;
        }
    };
    struct Context {
        const IPostingListFileBacking* const backing_store_file;
        bool                                 cache_miss;

        Context(const IPostingListFileBacking *backing_store_file_in) noexcept
            : backing_store_file(backing_store_file_in),
              cache_miss(false)
        {
        }
    };
    /*
     * Interface class for reading posting list on cache miss.
     */
    class IPostingListFileBacking {
    public:
        virtual ~IPostingListFileBacking() = default;
        virtual search::index::PostingListHandle read(const Key& key, Context& ctx) const = 0;
        virtual std::shared_ptr<BitVector> read(const BitVectorKey& key, Context& ctx) const = 0;
    };
    virtual ~IPostingListCache() = default;
    virtual search::index::PostingListHandle read(const Key& key, Context& ctx) const = 0;
    virtual std::shared_ptr<BitVector> read(const BitVectorKey& key, Context& ctx) const = 0;
    virtual vespalib::CacheStats get_stats() const = 0;
    virtual vespalib::CacheStats get_bitvector_stats() const = 0;
    virtual bool enabled_for_posting_lists() const noexcept = 0;
    virtual bool enabled_for_bitvectors() const noexcept = 0;
};

}
