// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumhintsearchcontext.h"
#include "i_enum_store_dictionary.h"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/vespalib/datastore/i_unique_store_dictionary_read_snapshot.h>


namespace search::attribute {

using queryeval::SearchIterator;
using fef::TermFieldMatchData;

EnumHintSearchContext::
EnumHintSearchContext(const IEnumStoreDictionary &dictionary,
                      uint32_t docIdLimit,
                      uint64_t numValues)
    : _dict_snapshot(dictionary.get_read_snapshot()),
      _uniqueValues(0u),
      _docIdLimit(docIdLimit),
      _numValues(numValues)
{
}


EnumHintSearchContext::~EnumHintSearchContext() = default;


void
EnumHintSearchContext::lookupTerm(const vespalib::datastore::EntryComparator &comp)
{
    _uniqueValues = _dict_snapshot->count(comp);
}


void
EnumHintSearchContext::lookupRange(const vespalib::datastore::EntryComparator &low,
                                   const vespalib::datastore::EntryComparator &high)
{
    _uniqueValues = _dict_snapshot->count_in_range(low, high);
}

void
EnumHintSearchContext::fetchPostings(const queryeval::ExecuteInfo &)
{
}

SearchIterator::UP
EnumHintSearchContext::createPostingIterator(TermFieldMatchData *, bool )
{
    return (_uniqueValues == 0u)
        ? std::make_unique<queryeval::EmptySearch>()
        : SearchIterator::UP();
}


unsigned int
EnumHintSearchContext::approximateHits() const
{
    return (_uniqueValues == 0u)
        ? 0u
        : std::max(uint64_t(_docIdLimit), _numValues);
}

}
