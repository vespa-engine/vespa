// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <cstdint>

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

/**
 * Interface for posting lists used by PredicateSearch.
 */
namespace search::predicate {

class PredicatePostingList {
    uint32_t _docId;
    uint64_t _subquery;

protected:
    PredicatePostingList()
        : _docId(0),
          _subquery(UINT64_MAX)
    { }

    void setDocId(uint32_t docId) { _docId = docId; }

public:
    using UP = std::unique_ptr<PredicatePostingList>;

    virtual ~PredicatePostingList() = default;

    /*
     * Moves to next document after the one supplied.
     * Returns false if there were no more doc ids.
     */
    virtual bool next(uint32_t docId) = 0;

    /*
     * Moves to the next interval within the current doc id.
     * Returns false if there were no more intervals for the current doc id.
     */
    virtual bool nextInterval() = 0;

    uint32_t getDocId() const { return _docId; }
    VESPA_DLL_LOCAL virtual uint32_t getInterval() const = 0;

    // Comes from the query that triggered inclusion of this posting list.
    void setSubquery(uint64_t subquery) { _subquery = subquery; }
    uint64_t getSubquery() const { return _subquery; }
};

}
