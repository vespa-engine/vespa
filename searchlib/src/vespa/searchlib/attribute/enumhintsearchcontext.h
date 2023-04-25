// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ipostinglistsearchcontext.h"

namespace vespalib::datastore {
    class EntryComparator;
    class IUniqueStoreDictionaryReadSnapshot;
}

namespace search { class IEnumStoreDictionary; }

namespace search::attribute {

/**
 * Search context helper for enumerated attributes, used to eliminate
 * searches for values that are not present at all.
 */

class EnumHintSearchContext : public IPostingListSearchContext
{
    const std::unique_ptr<vespalib::datastore::IUniqueStoreDictionaryReadSnapshot> _dict_snapshot;
    uint32_t                _uniqueValues;
    uint32_t                _docIdLimit;
    uint64_t                _numValues; // attr.getStatus().getNumValues();

protected:
    EnumHintSearchContext(const IEnumStoreDictionary &dictionary,
                          uint32_t docIdLimit,
                          uint64_t numValues);
    ~EnumHintSearchContext() override;

public:
    void lookupTerm(const vespalib::datastore::EntryComparator &comp);
    void lookupRange(const vespalib::datastore::EntryComparator &low, const vespalib::datastore::EntryComparator &high);

protected:
    std::unique_ptr<queryeval::SearchIterator>
    createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;

    void fetchPostings(const queryeval::ExecuteInfo & execInfo) override;
    unsigned int approximateHits() const override;
    uint32_t get_committed_docid_limit() const noexcept { return _docIdLimit; }
};

}
