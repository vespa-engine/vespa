// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <cstdint>

namespace search::index {

/**
 * Basic class for holding the result of a dictionary lookup result
 * for a word, to optimize query tree node child order and know from
 * where in the posting list files to read data.  A posting list with
 * 64 or fewer documents does not have skip info.
 */
class PostingListCounts {
public:
    /*
     * Nested class for describing a segment of a large posting list.
     * Very large posting lists are divided into segments, to limit
     * memory consumption (for buffering) and can be viewed as a
     * high level skip list stored in the dictionary.  If the posting
     * list for a word is less than 256 kB then it is not split into
     * segments.
     */
    class Segment
    {
    public:
        uint64_t _bitLength; // Length of segment
        uint32_t _numDocs;   // Number of documents in segment
        uint32_t _lastDoc;   // Last document id in segment

        Segment() noexcept
            : _bitLength(0),
              _numDocs(0),
              _lastDoc(0)
        { }

        bool
        operator==(const Segment &rhs) const noexcept {
            return (_bitLength == rhs._bitLength &&
                    _numDocs == rhs._numDocs &&
                    _lastDoc == rhs._lastDoc);
        }
    };

    /**
     * Counts might span multiple posting lists (i.e. multiple words
     * for prefix search), numDocs is then sum of documents for each posting
     * list, which segment info is absent.
     */
    uint64_t _numDocs;      // Number of documents for word(s)
    uint64_t _bitLength;    // Length of postings for word(s)

    /**
     * Very large posting lists with skip info are split into multiple
     * segments.  If there are more than one segments for a word then the
     * last segment has skip info even if it has fewer than 64 documents.
     */
    std::vector<Segment> _segments;

    PostingListCounts() noexcept
        : _numDocs(0),
          _bitLength(0),
          _segments()
    { }
    void swap(PostingListCounts & rhs) noexcept {
        std::swap(_numDocs, rhs._numDocs);
        std::swap(_bitLength, rhs._bitLength);
        std::swap(_segments, rhs._segments);
    }

    void clear() noexcept {
        _bitLength = 0;
        _numDocs = 0;
        _segments.clear();
    }

    bool operator==(const PostingListCounts &rhs) const noexcept {
        return (_numDocs == rhs._numDocs &&
                _bitLength == rhs._bitLength &&
                _segments == rhs._segments);
    }
};

void swap(PostingListCounts & a, PostingListCounts & b);


class PostingListOffsetAndCounts {
public:
    uint64_t _offset;
    uint64_t _accNumDocs;   // Used by prefix search for now.
    PostingListCounts _counts;

    PostingListOffsetAndCounts() noexcept
        : _offset(0),
          _accNumDocs(0u),
          _counts()
    { }
};

}
