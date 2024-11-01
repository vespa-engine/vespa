// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglisthandle.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <bit>

namespace search::diskindex {

/*
 * Interface class for caching posting lists read from disk.
 */
class IPostingListCache {
public:
    class IPostingListFileBacking;
    struct Key {
       IPostingListFileBacking* backing_store_file; // Used by backing store on cache miss
       uint64_t file_id;
       uint64_t bit_offset;
       uint64_t bit_length;
       Key() noexcept : backing_store_file(nullptr), file_id(0), bit_offset(0), bit_length(0) { }
       size_t hash() const noexcept { return std::rotl(file_id, 40) + bit_offset; }
       bool operator==(const Key& rhs) const noexcept {
           // Don't check backing_store_file, it is just passed in key for convenience
           return file_id == rhs.file_id &&
                  bit_offset == rhs.bit_offset &&
                  bit_length == rhs.bit_length;
       }
    };
    /*
     * Interface class for reading posting list on cache miss.
     */
    class IPostingListFileBacking {
    public:
        virtual ~IPostingListFileBacking() = default;
        virtual search::index::PostingListHandle read(const Key& key) = 0;
    };
    virtual ~IPostingListCache() = default;
    virtual search::index::PostingListHandle read(const Key& key) const = 0;
    virtual vespalib::CacheStats get_stats() const = 0;
};

}
