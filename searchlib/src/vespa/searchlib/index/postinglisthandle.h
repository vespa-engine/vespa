// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/index/postinglistcounts.h>
#include <memory>

namespace search { class BitVector; }
namespace search { namespace queryeval { class SearchIterator; } }
namespace search { namespace fef { class TermFieldMatchDataArray; } }

namespace search {
namespace index {

class PostingListFileRandRead;

/**
 * Class for owning a posting list in memory after having read it from
 * posting list file, or referencing a chunk of memory containing the
 * posting list (if the file was memory mapped).
 */
class PostingListHandle
{
public:
    typedef std::unique_ptr<PostingListHandle> UP;
    // Key portion
    PostingListFileRandRead *_file;	// File containing posting list
    uint64_t _bitOffset;	// posting list start relative to start of file
    uint64_t _bitLength;	// Length of posting list, in bits

    // Value portion
    uint32_t _firstSegment;	// First segment for word
    uint32_t _numSegments;	// Number of segments
    uint64_t _bitOffsetMem;	// _mem relative to start of file
    const void *_mem;		// Memory backing posting list after read/mmap
    void *_allocMem;		// What to free after posting list
    size_t _allocSize;		// Size of allocated memory

    PostingListHandle()
    : _file(NULL),
      _bitOffset(0),
      _bitLength(0),
      _firstSegment(0),
      _numSegments(0),
      _bitOffsetMem(0),
      _mem(NULL),
      _allocMem(NULL),
      _allocSize(0)
    { }

    ~PostingListHandle()
    {
        if (_allocMem != NULL)
            free(_allocMem);
    }

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     *
     * XXX: TODO: How to read next set of segments from disk if handle
     * didn't cover the whole word, probably need access to higher level
     * API above caches.
     */
    search::queryeval::SearchIterator *
    createIterator(const PostingListCounts &counts,
                   const search::fef::TermFieldMatchDataArray &matchData,
                   bool useBitVector=false) const;

    /**
     * Drop value portion of handle.
     */
    void drop() {
        _firstSegment = 0;
        _numSegments = 0;
        _bitOffsetMem = 0;
        _mem = NULL;
        if (_allocMem != NULL) {
            free(_allocMem);
            _allocMem = NULL;
        }
        _allocSize = 0;
    }
};


} // namespace search::index
} // namespace search

