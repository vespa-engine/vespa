// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include "ipostinglistsearchcontext.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search {

namespace datastore { class EntryComparator; }

namespace attribute {

/**
 * Search context helper for enumerated attributes, used to eliminate
 * searches for values that are not present at all.
 */

class EnumHintSearchContext : public IPostingListSearchContext
{
    const IEnumStoreDictionary::ReadSnapshot::UP _dict_snapshot;
    uint32_t                _uniqueValues;
    uint32_t                _docIdLimit;
    uint64_t                _numValues; // attr.getStatus().getNumValues();

protected:
    EnumHintSearchContext(const IEnumStoreDictionary &dictionary,
                          uint32_t docIdLimit,
                          uint64_t numValues);
    ~EnumHintSearchContext();

    void lookupTerm(const datastore::EntryComparator &comp);
    void lookupRange(const datastore::EntryComparator &low, const datastore::EntryComparator &high);

    queryeval::SearchIterator::UP
    createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;

    void fetchPostings(bool strict) override;
    unsigned int approximateHits() const override;
};

}
}
