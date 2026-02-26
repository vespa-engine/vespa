// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include "searchiterator.h"

namespace search::queryeval {

/**
 * Simple result class containing only document ids. This class will
 * mostly be used for testing.
 **/
class SimpleResult
{
private:
    std::vector<uint32_t> _hits;

public:
    /**
     * Create an empty result
     **/
    SimpleResult() noexcept : _hits() {}

    /**
     * Create a result with the given hits.
     */
    SimpleResult(const std::vector<uint32_t> &hits) : _hits(hits) {}

    /**
     * Obtain the number of hits
     *
     * @return number of hits
     **/
    uint32_t getHitCount() const { return _hits.size(); }

    /**
     * Get the docid of a specific hit
     *
     * @return docid for the i'th hit
     * @param i which hit to obtain
     **/
    uint32_t getHit(uint32_t i) const { return _hits[i]; }

    /**
     * Add a hit. Hits must be added in sorted order (smallest docid
     * first).
     *
     * @return this object for chaining
     * @param docid hit to add
     **/
    SimpleResult &addHit(uint32_t docid);

    /**
     * remove all hits
     **/
    void clear();

    /**
     * Fill this result with all the hits returned by the given search
     * object. Old hits will be removed from this result before doing
     * the search.
     *
     * @param sb search object
     * @param docIdLimit the end of the docId range for this search iterator
     **/
    SimpleResult &search(SearchIterator &sb, uint32_t docIdLimit);

    /**
     * Test of we contain the same hits as rhs.
     *
     * @return true if the results are equal
     * @param rhs other results
     **/
    bool operator==(const SimpleResult &rhs) const { return (_hits == rhs._hits); }

    bool contains(const SimpleResult& subset) const;
};

std::ostream &operator << (std::ostream &out, const SimpleResult &result);

}
