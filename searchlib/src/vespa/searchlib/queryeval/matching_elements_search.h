// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/i_direct_posting_store.h>
#include <cstdint>
#include <memory>
#include <span>
#include <vector>

namespace search { class MatchingElements; }
namespace search::attribute { class IAttributeVector; }
namespace vespalib::datastore { class EntryRef; }

namespace search::queryeval {

/*
 * Class used to find matching elements to limit what is returned in dynamic
 * summaries for summary fields where matched-elements-only is set.
 */
class MatchingElementsSearch {
protected:
    std::vector<uint32_t> _matching_elements;
public:
    MatchingElementsSearch();
    virtual ~MatchingElementsSearch();
    virtual void find_matching_elements(uint32_t doc_id, MatchingElements& result) = 0;
    virtual void initRange(uint32_t begin_id, uint32_t end_id) = 0;

    static std::unique_ptr<MatchingElementsSearch> create(const search::attribute::IAttributeVector &attr, const std::string& field_name, vespalib::datastore::EntryRef dictionary_snapshot, std::span<const IDirectPostingStore::LookupResult> dict_entries);
};

}
